/*

Copyright 2017, Owen Stephens.
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
    * Neither the name of the copyright holder nor the names of its
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

package com.google.refine.util;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

public class PatternSyntaxExceptionParser {

    private static final Map<String, String> DESCRIPTION_TO_MESSAGE = new HashMap<>();

    static {
        DESCRIPTION_TO_MESSAGE.put(
                "Unclosed character class",
                "The regular expression is missing a closing ']' character, or has an empty pair of square brackets '[]'."
        );
        DESCRIPTION_TO_MESSAGE.put(
                "Unmatched closing ')'",
                "The regular expression is missing an opening '(' character."
        );
        DESCRIPTION_TO_MESSAGE.put(
                "Unclosed group",
                "The regular expression is missing a closing ')' character."
        );
        DESCRIPTION_TO_MESSAGE.put(
                "Dangling meta character '*'",
                "The regular expression has a '*','+' or '?' in the wrong place."
        );
        DESCRIPTION_TO_MESSAGE.put(
                "Dangling meta character '+'",
                "The regular expression has a '*','+' or '?' in the wrong place."
        );
        DESCRIPTION_TO_MESSAGE.put(
                "Dangling meta character '?'",
                "The regular expression has a '*','+' or '?' in the wrong place."
        );
        DESCRIPTION_TO_MESSAGE.put(
                "Unexpected internal error",
                "The regular expression has a backslash '\\' at the end."
        );
        DESCRIPTION_TO_MESSAGE.put(
                "Unescaped trailing backslash",
                "The regular expression has a backslash '\\' at the end."
        );
        DESCRIPTION_TO_MESSAGE.put(
                "Unclosed counted closure",
                "The regular expression is missing a closing '}' character, or has an incorrect quantifier statement in curly brackets '{}'."
        );
        DESCRIPTION_TO_MESSAGE.put(
                "Illegal repetition",
                "The regular expression has an incomplete or incorrect quantifier statement in curly brackets '{}'."
        );
        DESCRIPTION_TO_MESSAGE.put(
                "Illegal repetition range",
                "The regular expression has a quantifier statement where the minimum is larger than the maximum (e.g. {4,3})."
        );
        DESCRIPTION_TO_MESSAGE.put(
                "Illegal character range",
                "The regular expression has a range statement which is incomplete or has the characters in the incorrect order (e.g. [9-0])."
        );
    }

    private final PatternSyntaxException exception;

    public PatternSyntaxExceptionParser(PatternSyntaxException e) {
        this.exception = e;
    }

    public String getUserMessage() {
        String desc = exception.getDescription();
        return DESCRIPTION_TO_MESSAGE.getOrDefault(desc, exception.getMessage());
    }
}
