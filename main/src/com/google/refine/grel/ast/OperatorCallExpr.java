/*
Copyright 2010, Google Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the
      distribution.
    * Neither the name of Google Inc. nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

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

package com.google.refine.grel.ast;

import java.text.Collator;
import java.util.Properties;

import com.google.refine.expr.Evaluable;
import com.google.refine.expr.ExpressionUtils;

public class OperatorCallExpr implements Evaluable {

    private final Evaluable[] _args;
    private final String _op;

    public OperatorCallExpr(Evaluable[] args, String op) {
        _args = args;
        _op = op;
    }

    @Override
    public Object evaluate(Properties bindings) {
        Object[] evaluatedArgs = evaluateArguments(bindings);
        if (evaluatedArgs == null) {
            return null;

        }

        if (evaluatedArgs.length == 2) {
            Object result = handleBinaryOperation(evaluatedArgs[0], evaluatedArgs[1]);
            if (result != null) {
                return result;
            }
        }

        if ("==".equals(_op)) {
            return handleEquality(evaluatedArgs[0], evaluatedArgs.length > 1 ? evaluatedArgs[1] : null, true);
        } else if ("!=".equals(_op)) {
            return handleEquality(evaluatedArgs[0], evaluatedArgs.length > 1 ? evaluatedArgs[1] : null, false);
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Evaluable ev : _args) {
            if (sb.length() > 0) {
                sb.append(' ').append(_op).append(' ');
            }
            sb.append(ev.toString());
        }
        return sb.toString();
    }

    private Object[] evaluateArguments(Properties bindings) {
        Object[] args = new Object[_args.length];
        for (int i = 0; i < _args.length; i++) {
            Object value = _args[i].evaluate(bindings);
            if (ExpressionUtils.isError(value)) {
                return null;
            }
            args[i] = value;
        }
        return args;
    }

    private Object handleBinaryOperation(Object left, Object right) {
        if (isIntegral(left) && isIntegral(right)) {
            return handleLongArithmetic(((Number) left).longValue(), ((Number) right).longValue());
        }
        if (left instanceof Number && right instanceof Number) {
            return handleDoubleArithmetic(((Number) left).doubleValue(), ((Number) right).doubleValue());
        }
        if (left instanceof String && right instanceof String) {
            Object result = handleStringComparison((String) left, (String) right);
            if (result != null) {
                return result;
            }
            if ("+".equals(_op)) {
                return ((String) left) + ((String) right);
            }
        }
        if ((left instanceof String || right instanceof String) && "+".equals(_op)) {
            return left.toString() + right.toString();
        }
        if (left instanceof Comparable && right instanceof Comparable) {
            if (compatibleClasses(left, right)) {
                return handleComparableComparison((Comparable<?>) left, (Comparable<?>) right);
            }
        }
        return null;
    }

    private Object handleLongArithmetic(long n1, long n2) {
        switch (_op) {
            case "+":
                return n1 + n2;
            case "-":
                return n1 - n2;
            case "*":
                return n1 * n2;
            case "/":
                if (n2 == 0 && n1 == 0) {
                    return Double.NaN;
                }
                return n1 / n2;
            case "%":
                return n1 % n2;
            case ">":
                return n1 > n2;
            case ">=":
                return n1 >= n2;
            case "<":
                return n1 < n2;
            case "<=":
                return n1 <= n2;
            case "==":
                return n1 == n2;
            case "!=":
                return n1 != n2;
            default:
                return null;
        }
    }

    private Object handleDoubleArithmetic(double d1, double d2) {
        switch (_op) {
            case "+":
                return d1 + d2;
            case "-":
                return d1 - d2;
            case "*":
                return d1 * d2;
            case "/":
                if (d2 == 0 && d1 == 0) {
                    return Double.NaN;
                }
                return d1 / d2;
            case "%":
                return d1 % d2;
            case ">":
                return d1 > d2;
            case ">=":
                return d1 >= d2;
            case "<":
                return d1 < d2;
            case "<=":
                return d1 <= d2;
            case "==":
                return d1 == d2;
            case "!=":
                return d1 != d2;
            default:
                return null;
        }
    }

    private Object handleStringComparison(String s1, String s2) {
        Collator collator = createDefaultCollator();

        switch (_op) {
            case ">":
                return collator.compare(s1, s2) > 0;
            case ">=":
                return collator.compare(s1, s2) >= 0;
            case "<":
                return collator.compare(s1, s2) < 0;
            case "<=":
                return collator.compare(s1, s2) <= 0;
            case "==":
                return collator.compare(s1, s2) == 0;
            case "!=":
                return collator.compare(s1, s2) != 0;
            default:
                return null;
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Object handleComparableComparison(Comparable left, Comparable right) {
        int cmp = left.compareTo(right);
        switch (_op) {
            case ">":
                return cmp > 0;
            case ">=":
                return cmp >= 0;
            case "<":
                return cmp < 0;
            case "<=":
                return cmp <= 0;
            case "==":
                return cmp == 0;
            case "!=":
                return cmp != 0;
            default:
                return null;
        }
    }

    private Object handleEquality(Object left, Object right, boolean isEquality) {
        if (left != null) {
            boolean eq = left.equals(right);
            return isEquality ? eq : !eq;
        } else {
            boolean bothNull = (right == null);
            return isEquality ? bothNull : !bothNull;
        }
    }

    private Collator createDefaultCollator() {
        Collator collator = Collator.getInstance();
        collator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
        return collator;
    }

    private boolean compatibleClasses(Object left, Object right) {
        return left.getClass().isAssignableFrom(right.getClass())
                || right.getClass().isAssignableFrom(left.getClass());
    }

    private boolean isIntegral(Object n) {
        return n instanceof Long || n instanceof Integer;
    }
}
