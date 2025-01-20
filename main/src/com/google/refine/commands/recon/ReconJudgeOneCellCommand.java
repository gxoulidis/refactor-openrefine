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

package com.google.refine.commands.recon;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.refine.commands.Command;
import com.google.refine.expr.ExpressionUtils;
import com.google.refine.history.Change;
import com.google.refine.history.HistoryEntry;
import com.google.refine.model.Cell;
import com.google.refine.model.Column;
import com.google.refine.model.Project;
import com.google.refine.model.Recon;
import com.google.refine.model.Recon.Judgment;
import com.google.refine.model.ReconCandidate;
import com.google.refine.model.ReconStats;
import com.google.refine.model.changes.CellChange;
import com.google.refine.model.changes.ReconChange;
import com.google.refine.process.QuickHistoryEntryProcess;
import com.google.refine.util.Pool;

public class ReconJudgeOneCellCommand extends Command {

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        if (!hasValidCSRFToken(request)) {
            respondCSRFError(response);
            return;
        }

        try {
            request.setCharacterEncoding("UTF-8");
            response.setCharacterEncoding("UTF-8");

            Project project = getProject(request);

            int rowIndex = Integer.parseInt(request.getParameter("row"));
            int cellIndex = Integer.parseInt(request.getParameter("cell"));
            Judgment judgment = Recon.stringToJudgment(request.getParameter("judgment"));

            // Prepare a possible match candidate
            ReconCandidate match = null;
            String id = request.getParameter("id");
            if (id != null) {
                String scoreString = request.getParameter("score");
                double score = (scoreString != null) ? Double.parseDouble(scoreString) : 100;

                match = new ReconCandidate(
                        id,
                        request.getParameter("name"),
                        request.getParameter("types").split(","),
                        score
                );
            }

            // Create and queue our process
            JudgeOneCellProcess process = new JudgeOneCellProcess(
                    project,
                    "Judge one cell's recon result",
                    judgment,
                    rowIndex,
                    cellIndex,
                    match,
                    request.getParameter("identifierSpace"),
                    request.getParameter("schemaSpace")
            );

            // Execute or queue the process
            HistoryEntry historyEntry = project.processManager.queueProcess(process);
            if (historyEntry != null) {
                // If the process is done, respond with updated cell data
                Pool pool = new Pool();
                if (process.newCell != null && process.newCell.recon != null) {
                    pool.pool(process.newCell.recon);
                }

                // Using the existing ReconClearOneCellCommand's CellResponse for JSON response
                respondJSON(response, new ReconClearOneCellCommand.CellResponse(historyEntry, process.newCell, pool));
            } else {
                // Process is still pending
                respond(response, "{ \"code\" : \"pending\" }");
            }

        } catch (Exception e) {
            respondException(response, e);
        }
    }

    /**
     * Inner class that performs the "judge one cell" logic as a process.
     */
    protected static class JudgeOneCellProcess extends QuickHistoryEntryProcess {

        private final int rowIndex;
        private final int cellIndex;
        private final Judgment judgment;
        private final ReconCandidate match;
        private final String identifierSpace;
        private final String schemaSpace;

        Cell newCell; // We'll fill this in createHistoryEntry(...)

        JudgeOneCellProcess(
                Project project,
                String briefDescription,
                Judgment judgment,
                int rowIndex,
                int cellIndex,
                ReconCandidate match,
                String identifierSpace,
                String schemaSpace
        ) {
            super(project, briefDescription);
            this.judgment = judgment;
            this.rowIndex = rowIndex;
            this.cellIndex = cellIndex;
            this.match = match;
            this.identifierSpace = identifierSpace;
            this.schemaSpace = schemaSpace;
        }

        @Override
        protected HistoryEntry createHistoryEntry(long historyEntryID) throws Exception {
            Cell oldCell = validateCellAndColumn(rowIndex, cellIndex);

            Column column = _project.columnModel.getColumnByCellIndex(cellIndex);
            Recon oldRecon = (oldCell.recon != null) ? oldCell.recon : null;
            Recon newRecon = prepareRecon(oldRecon, column, historyEntryID);

            String description = applyJudgment(newRecon, oldCell, column.getName());

            ReconStats stats = column.getReconStats();
            if (stats == null) {
                stats = ReconStats.create(_project, cellIndex);
            } else {
                Judgment oldJudgment = (oldRecon == null) ? Judgment.None : oldRecon.judgment;
                stats = updateStats(stats, oldJudgment, newRecon.judgment);
            }

            newCell = new Cell(oldCell.value, newRecon);
            Change change = new ReconChange(
                    new CellChange(rowIndex, cellIndex, oldCell, newCell),
                    column.getName(),
                    column.getReconConfig(),
                    stats
            );

            return new HistoryEntry(
                    historyEntryID,
                    _project,
                    description,
                    null,
                    change
            );
        }

        private Cell validateCellAndColumn(int row, int cellIdx) throws Exception {
            Cell cell = _project.rows.get(row).getCell(cellIdx);
            if (cell == null || !ExpressionUtils.isNonBlankData(cell.value)) {
                throw new Exception("Cell is blank or error");
            }
            Column column = _project.columnModel.getColumnByCellIndex(cellIdx);
            if (column == null) {
                throw new Exception("No such column");
            }
            return cell;
        }

        private Recon prepareRecon(Recon oldRecon, Column column, long historyEntryID) {
            if (oldRecon != null) {
                return oldRecon.dup(historyEntryID);
            }
            else if (identifierSpace != null && schemaSpace != null) {
                return new Recon(historyEntryID, identifierSpace, schemaSpace);
            }
            else if (column.getReconConfig() != null) {
                return column.getReconConfig().createNewRecon(historyEntryID);
            }
            else {
                return new Recon(historyEntryID, null, null);
            }
        }

        private String applyJudgment(Recon newRecon, Cell oldCell, String columnName) {
            newRecon.matchRank = -1;
            newRecon.judgmentAction = "single";
            newRecon.judgmentBatchSize = 1;

            String cellDescription = "single cell on row " + (rowIndex + 1) +
                    ", column " + columnName +
                    ", containing \"" + oldCell.value + "\"";

            switch (judgment) {
                case None:
                    if (oldCell.recon != null && oldCell.recon.error != null) {
                        newRecon.judgment = Judgment.Error;
                    } else {
                        newRecon.judgment = Judgment.None;
                    }
                    newRecon.match = null;
                    return "Discard recon judgment for " + cellDescription;

                case Error:
                    throw new IllegalArgumentException("Cannot manually set judgment to 'error'");

                case New:
                    newRecon.judgment = Judgment.New;
                    newRecon.match = null;
                    return "Mark to create new item for " + cellDescription;

                case Matched:
                    newRecon.judgment = Judgment.Matched;
                    newRecon.match = this.match;
                    if (newRecon.candidates != null && this.match != null) {
                        // Try to find match rank in existing candidates
                        for (int m = 0; m < newRecon.candidates.size(); m++) {
                            if (newRecon.candidates.get(m).id.equals(this.match.id)) {
                                newRecon.matchRank = m;
                                break;
                            }
                        }
                    }
                    return "Match " + (this.match != null ? this.match.name : "unknown") +
                            " (" + (this.match != null ? this.match.id : "null") + ") to " +
                            cellDescription;

                default:
                    newRecon.judgment = Judgment.None;
                    newRecon.match = null;
                    return "No recognized judgment found for " + cellDescription;
            }
        }

        private ReconStats updateStats(ReconStats stats, Judgment oldJ, Judgment newJ) {
            int newChange    = calcDelta(oldJ, newJ, Judgment.New);
            int matchChange  = calcDelta(oldJ, newJ, Judgment.Matched);
            int errorsChange = calcDelta(oldJ, newJ, Judgment.Error);

            return new ReconStats(
                    stats.nonBlanks,
                    stats.newTopics + newChange,
                    stats.matchedTopics + matchChange,
                    stats.errorTopics + errorsChange
            );
        }

        private int calcDelta(Judgment oldJ, Judgment newJ, Judgment target) {
            int delta = 0;
            if (oldJ == target) {
                delta--;
            }
            if (newJ == target) {
                delta++;
            }
            return delta;
        }
    }
}
