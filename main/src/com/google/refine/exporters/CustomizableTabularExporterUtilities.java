/*

Copyright 2010, Google Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
    * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

*/

package com.google.refine.exporters;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.TimeZone;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;

import com.google.refine.ProjectManager;
import com.google.refine.browsing.Engine;
import com.google.refine.browsing.FilteredRows;
import com.google.refine.browsing.RowVisitor;
import com.google.refine.exporters.TabularSerializer;
import com.google.refine.exporters.TabularSerializer.CellData;
import com.google.refine.model.Cell;
import com.google.refine.model.Column;
import com.google.refine.model.Project;
import com.google.refine.model.Recon;
import com.google.refine.model.Recon.Judgment;
import com.google.refine.model.Row;
import com.google.refine.preference.PreferenceStore;
import com.google.refine.util.JSONUtilities;
import com.google.refine.util.ParsingUtilities;


abstract public class CustomizableTabularExporterUtilities {

    final static private String fullIso8601 = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    static public void exportRows(
            final Project project,
            final Engine engine,
            Properties params,
            final TabularSerializer serializer) {

        JsonNode options = parseExporterOptions(params);

        final boolean outputColumnHeaders = JSONUtilities.getBoolean(options, "outputColumnHeaders", true);
        final boolean outputEmptyRows     = JSONUtilities.getBoolean(options, "outputBlankRows", true);
        final int limit                   = JSONUtilities.getInt(options, "limit", -1);

        final List<String> columnNames = new ArrayList<>();
        final Map<String, CellFormatter> colNameToFormatter =
                determineColumnsAndFormatters(project, options, columnNames);

        RowVisitor visitor = createRowVisitor(
                serializer,
                columnNames,
                colNameToFormatter,
                outputColumnHeaders,
                outputEmptyRows,
                limit
        );

        FilteredRows filteredRows = engine.getAllFilteredRows();
        filteredRows.accept(project, visitor);
    }


    static public int[] countColumnsRows(
            final Project project,
            final Engine engine,
            Properties params) {

        RowCountingTabularSerializer serializer = new RowCountingTabularSerializer();
        exportRows(project, engine, params, serializer);
        return new int[] { serializer.columns, serializer.rows };
    }


    private static JsonNode parseExporterOptions(Properties params) {
        if (params == null) {
            return null;
        }
        String optionsString = params.getProperty("options");
        if (optionsString != null) {
            try {
                return ParsingUtilities.mapper.readTree(optionsString);
            } catch (IOException e) {
            }
        }
        return null;
    }

    private static Map<String, CellFormatter> determineColumnsAndFormatters(
            Project project,
            JsonNode options,
            List<String> columnNamesOut) {

        Map<String, CellFormatter> nameToFormatter = new HashMap<>();

        List<JsonNode> columnOptionsArray = (options == null) ? null : JSONUtilities.getArray(options, "columns");

        if (columnOptionsArray == null) {
            List<Column> columns = project.columnModel.columns;
            for (Column column : columns) {
                String colName = column.getName();
                columnNamesOut.add(colName);
                nameToFormatter.put(colName, new CellFormatter());
            }
        } else {
            for (JsonNode colOptions : columnOptionsArray) {
                if (colOptions != null) {
                    String name = JSONUtilities.getString(colOptions, "name", null);
                    if (name != null) {
                        columnNamesOut.add(name);
                        try {
                            ColumnOptions formatter =
                                    ParsingUtilities.mapper.treeToValue(colOptions, ColumnOptions.class);
                            nameToFormatter.put(name, formatter);
                        } catch (JsonProcessingException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        return nameToFormatter;
    }

    private static RowVisitor createRowVisitor(
            final TabularSerializer serializer,
            final List<String> columnNames,
            final Map<String, CellFormatter> columnNameToFormatter,
            final boolean outputColumnHeaders,
            final boolean outputEmptyRows,
            final int limit) {

        return new RowVisitor() {
            int rowCount = 0;

            @Override
            public void start(Project project) {
                serializer.startFile(null);
                if (outputColumnHeaders) {
                    List<CellData> headerCells = new ArrayList<>(columnNames.size());
                    for (String name : columnNames) {
                        headerCells.add(new CellData(name, name, name, null));
                    }
                    serializer.addRow(headerCells, true);
                }
            }

            @Override
            public boolean visit(Project project, int rowIndex, Row row) {
                List<CellData> cells = new ArrayList<>(columnNames.size());
                int nonNullCount = 0;

                for (String columnName : columnNames) {
                    Column column = project.columnModel.getColumnByName(columnName);
                    CellFormatter formatter = columnNameToFormatter.get(columnName);

                    if (column == null || formatter == null) {
                        cells.add(null);
                        continue;
                    }

                    Cell cell = row.getCell(column.getCellIndex());
                    CellData cellData = formatter.format(project, column, cell);

                    cells.add(cellData);
                    if (cellData != null) {
                        nonNullCount++;
                    }
                }

                if (nonNullCount > 0 || outputEmptyRows) {
                    serializer.addRow(cells, false);
                    rowCount++;
                }

                return (limit > 0 && rowCount >= limit);
            }

            @Override
            public void end(Project project) {
                serializer.endFile();
            }
        };
    }


    static private class RowCountingTabularSerializer implements TabularSerializer {

        int columns;
        int rows;

        @Override
        public void startFile(JsonNode options) {
            // no-op
        }

        @Override
        public void endFile() {
            // no-op
        }

        @Override
        public void addRow(List<CellData> cells, boolean isHeader) {
            if (cells != null) {
                columns = Math.max(columns, cells.size());
            }
            rows++;
        }
    }


    private enum ReconOutputMode {
        @JsonProperty("entity-name") ENTITY_NAME,
        @JsonProperty("entity-id")   ENTITY_ID,
        @JsonProperty("cell-content") CELL_CONTENT
    }

    private enum DateFormatMode {
        @JsonProperty("iso-8601")     ISO_8601,
        @JsonProperty("locale-short") SHORT_LOCALE,
        @JsonProperty("locale-medium") MEDIUM_LOCALE,
        @JsonProperty("locale-long")  LONG_LOCALE,
        @JsonProperty("locale-full")  FULL_LOCALE,
        @JsonProperty("custom")       CUSTOM
    }

    static private class ReconSettings {

        @JsonProperty("output")
        ReconOutputMode outputMode = ReconOutputMode.ENTITY_NAME;

        @JsonProperty("blankUnmatchedCells")
        boolean blankUnmatchedCells = false;

        @JsonProperty("linkToEntityPages")
        boolean linkToEntityPages = true;
    }

    static private class DateSettings {

        @JsonProperty("format")
        DateFormatMode formatMode = DateFormatMode.ISO_8601;

        @JsonProperty("custom")
        String custom = null;

        @JsonProperty("useLocalTimeZone")
        boolean useLocalTimeZone = false;

        @JsonProperty("omitTime")
        boolean omitTime = false;
    }

    static public class ColumnOptions extends CellFormatter {
        @JsonProperty("name")
        String columnName;
    }


    static public class CellFormatter {

        @JsonProperty("reconSettings")
        ReconSettings recon = new ReconSettings();

        @JsonProperty("dateSettings")
        DateSettings date = new DateSettings();

        @JsonProperty("nullValueToEmptyStr")
        boolean includeNullFieldValue = false;

        DateFormat dateFormatter;
        String[] urlSchemes     = { "http", "https", "ftp" };
        UrlValidator urlValidator = new UrlValidator(urlSchemes);

        Map<String, String> identifierSpaceToUrl = null;

        @JsonCreator
        CellFormatter(
                @JsonProperty("reconSettings") ReconSettings reconSettings,
                @JsonProperty("dateSettings") DateSettings dateSettings,
                @JsonProperty("nullValueToEmptyStr") boolean includeNullFieldValue
        ) {
            if (reconSettings != null) {
                recon = reconSettings;
            }
            if (dateSettings != null) {
                date = dateSettings;
            }
            this.includeNullFieldValue = includeNullFieldValue;
            setup();
        }

        CellFormatter() {
            setup();
        }


        CellData format(Project project, Column column, Cell cell) {
            if (cell == null) {
                return handleNullCell(column);
            }

            CellData reconData = formatReconCell(cell);
            if (reconData != null) {

                if (reconData.value == null && recon.blankUnmatchedCells) {
                    return null;
                }
                if (reconData.text != null) {
                    reconData.columnName = column.getName();
                    return reconData;
                }
            }

            return formatValue(column.getName(), cell, reconData);
        }


        private CellData handleNullCell(Column column) {
            if (includeNullFieldValue) {
                return new CellData(column.getName(), "", "", "");
            }
            return null;
        }

        private CellData formatReconCell(Cell cell) {
            Recon r = cell.recon;
            if (r == null) {
                return null;
            }

            if (r.judgment == Judgment.Matched) {
                String textOverride = null;
                switch (recon.outputMode) {
                    case ENTITY_NAME:
                        textOverride = r.match != null ? r.match.name : null;
                        break;
                    case ENTITY_ID:
                        textOverride = r.match != null ? r.match.id : null;
                        break;
                    case CELL_CONTENT:
                    default:
                        break;
                }

                String link = (recon.linkToEntityPages && r.match != null)
                        ? buildReconLink(r)
                        : null;

                if (textOverride != null) {
                    return new CellData(null, r.match, textOverride, link);
                }

            } else {
                if (recon.blankUnmatchedCells) {
                    return new CellData(null, null, null, null);
                }
            }
            return null;
        }

        private String buildReconLink(Recon r) {
            buildIdentifierSpaceToUrlMap();
            if (r.service == null || r.match == null) {
                return null;
            }
            String baseUrl = identifierSpaceToUrl.get(r.service);
            if (baseUrl == null) {
                return null;
            }
            return StringUtils.replace(baseUrl, "{{id}}", r.match.id);
        }

        private CellData formatValue(String columnName, Cell cell, CellData preFilled) {
            Object rawValue = cell.value;
            if (rawValue == null) {
                return preFilled; // or null
            }

            String text      = null;
            String link      = null;
            Object dataValue = rawValue;

            if (rawValue instanceof String) {
                text = (String) rawValue;
                link = maybeCreateLink(text);
            } else if (rawValue instanceof OffsetDateTime) {
                text = ((OffsetDateTime) rawValue).format(DateTimeFormatter.ISO_INSTANT);
            } else {
                text = rawValue.toString();
            }

            if (preFilled != null) {
                if (preFilled.link == null && link != null) {
                    preFilled.link = link;
                }
                if (preFilled.value == null) {
                    preFilled.value = dataValue;
                }
                if (preFilled.text == null) {
                    preFilled.text = text;
                }
                preFilled.columnName = columnName;
                return preFilled;
            }
            return new CellData(columnName, dataValue, text, link);
        }


        private String maybeCreateLink(String text) {
            if (text.contains(":") && urlValidator.isValid(text)) {
                try {
                    return new URI(text).toString();
                } catch (URISyntaxException e) {
                }
            }
            return null;
        }

        void buildIdentifierSpaceToUrlMap() {
            if (identifierSpaceToUrl != null) {
                return;
            }

            identifierSpaceToUrl = new HashMap<>();

            PreferenceStore ps = ProjectManager.singleton.getPreferenceStore();
            ArrayNode services = (ArrayNode) ps.get("reconciliation.standardServices");
            if (services == null) {
                return;
            }

            for (int i = 0; i < services.size(); i++) {
                ObjectNode service = (ObjectNode) services.get(i);
                ObjectNode view = JSONUtilities.getObject(service, "view");
                if (view != null) {
                    String url = JSONUtilities.getString(service, "url", null);
                    String viewUrl = JSONUtilities.getString(view, "url", null);
                    if (url != null && viewUrl != null) {
                        identifierSpaceToUrl.put(url, viewUrl);
                    }
                }
            }
        }


        private void setup() {
            this.dateFormatter = createDateFormatter();
            if (!date.useLocalTimeZone) {
                dateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
            }
        }

        private DateFormat createDateFormatter() {
            switch (date.formatMode) {
                case SHORT_LOCALE:
                    return createLocaleFormatter(SimpleDateFormat.SHORT);
                case MEDIUM_LOCALE:
                    return createLocaleFormatter(SimpleDateFormat.MEDIUM);
                case LONG_LOCALE:
                    return createLocaleFormatter(SimpleDateFormat.LONG);
                case FULL_LOCALE:
                    return createLocaleFormatter(SimpleDateFormat.FULL);
                case CUSTOM:
                    if (date.custom == null || date.custom.isEmpty()) {
                        return new SimpleDateFormat(fullIso8601);
                    }
                    return new SimpleDateFormat(date.custom);
                case ISO_8601:
                default:
                    return date.omitTime
                            ? new SimpleDateFormat("yyyy-MM-dd")
                            : new SimpleDateFormat(fullIso8601);
            }
        }

        private DateFormat createLocaleFormatter(int style) {
            return date.omitTime
                    ? SimpleDateFormat.getDateInstance(style)
                    : SimpleDateFormat.getDateTimeInstance(style, style);
        }
    }
}
