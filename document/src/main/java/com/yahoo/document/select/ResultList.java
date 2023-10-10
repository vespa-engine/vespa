// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select;

import com.yahoo.document.datatypes.FieldPathIteratorHandler;
import com.yahoo.document.select.rule.AttributeNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ResultList {
    public static class ResultPair {
        ResultPair(FieldPathIteratorHandler.VariableMap var, Result res) {
            variables = var;
            result = res;
        }

        FieldPathIteratorHandler.VariableMap variables;
        Result result;

        public FieldPathIteratorHandler.VariableMap getVariables() { return variables; }
        public Result getResult() { return result; }

        public String toString() {
            return variables.toString() + " => " + result;
        }
    }

    public static class VariableValue {
       public VariableValue(FieldPathIteratorHandler.VariableMap vars, Object value) {
           variables = vars;
           this.value = value;
       }

        FieldPathIteratorHandler.VariableMap variables;
        Object value;

        public FieldPathIteratorHandler.VariableMap getVariables() { return variables; }
        public Object getValue() { return value; }

        public String toString() {
            return variables.toString() + " => " + value;
        }
    }

    private List<ResultPair> results = new ArrayList<>();

    public ResultList() {
    }

    public ResultList(Result result) {
        add(new FieldPathIteratorHandler.VariableMap(), result);
    }

    public void add(FieldPathIteratorHandler.VariableMap variables, Result result) {
        results.add(new ResultPair(variables, result));
    }

    public List<ResultPair> getResults() {
        return results;
    }

    public static ResultList fromBoolean(boolean result) {
        return new ResultList(result ? Result.TRUE : Result.FALSE);
    }

    public Result toResult() {
        if (results.isEmpty()) {
            return Result.FALSE;
        }

        boolean foundFalse = false;

        for (ResultPair rp : results) {
            if (rp.result == Result.TRUE) {
                return Result.TRUE;
            } else if (rp.result == Result.FALSE) {
                foundFalse = true;
            }
        }

        if (foundFalse) {
            return Result.FALSE;
        } else {
            return Result.INVALID;
        }
    }

    private boolean combineVariables(FieldPathIteratorHandler.VariableMap output, FieldPathIteratorHandler.VariableMap input) {
        // First, verify that all variables are overlapping
        for (Map.Entry<String, FieldPathIteratorHandler.IndexValue> entry : output.entrySet()) {
            FieldPathIteratorHandler.IndexValue found = input.get(entry.getKey());

            if (found != null) {
                if (!(found.equals(entry.getValue()))) {
                    return false;
                }
            }
        }

        for (Map.Entry<String, FieldPathIteratorHandler.IndexValue> entry : input.entrySet()) {
            FieldPathIteratorHandler.IndexValue found = output.get(entry.getKey());

            if (found != null) {
                if (!(found.equals(entry.getValue()))) {
                    return false;
                }
            }
        }

        // Ok, variables are overlapping. Add all variables from input to output.
        for (Map.Entry<String, FieldPathIteratorHandler.IndexValue> entry : input.entrySet()) {
            output.put(entry.getKey(), entry.getValue());
        }

        return true;
    }

    public interface LazyResultList {
        ResultList getResult();
    }

    public ResultList combineAND(LazyResultList other)
    {
        if (Result.FALSE == toResult()) return ResultList.toResultList(false);

        ResultList result = new ResultList();

        // TODO: optimize
        for (ResultPair pair : results) {
            for (ResultPair otherPair : other.getResult().results) {
                FieldPathIteratorHandler.VariableMap varMap = (FieldPathIteratorHandler.VariableMap)pair.variables.clone();

                if (combineVariables(varMap, otherPair.variables)) {
                    result.add(varMap, combineAND(pair.result, otherPair.result));
                }
            }
        }

        return result;
    }

    private static Result combineAND(Result lhs, Result rhs) {
        if (lhs == Result.TRUE && rhs == Result.TRUE) {
            return Result.TRUE;
        }
        if (lhs == Result.FALSE || rhs == Result.FALSE) {
            return Result.FALSE;
        }
        return Result.INVALID;
    }

    public ResultList combineOR(LazyResultList other)
    {
        if (Result.TRUE == toResult()) return ResultList.toResultList(true);

        ResultList result = new ResultList();

        // TODO: optimize
        for (ResultPair pair : results) {
            for (ResultPair otherPair : other.getResult().results) {
                FieldPathIteratorHandler.VariableMap varMap = (FieldPathIteratorHandler.VariableMap)pair.variables.clone();

                if (combineVariables(varMap, otherPair.variables)) {
                    result.add(varMap, combineOR(pair.result, otherPair.result));
                }
            }
        }

        return result;
    }

    private static Result combineOR(Result lhs, Result rhs) {
        if (lhs == Result.TRUE || rhs == Result.TRUE) {
            return Result.TRUE;
        }
        if (lhs == Result.FALSE && rhs == Result.FALSE) {
            return Result.FALSE;
        }
        return Result.INVALID;
    }

    /**
     * Converts the given object value into a result list, so it can be compared by logical operators.
     *
     * @param value The object to convert.
     * @return The corresponding result value.
     */
    public static ResultList toResultList(Object value) {
        if (value instanceof ResultList) {
            return (ResultList)value;
        } else if (value instanceof AttributeNode.VariableValueList) {
            ResultList retVal = new ResultList();
            for (VariableValue vv : (AttributeNode.VariableValueList)value) {
                retVal.add(vv.getVariables(), Result.TRUE);
            }
            return retVal;
        } else if (value == null || value == Result.FALSE || value == Boolean.FALSE ||
            (value instanceof Number && ((Number)value).doubleValue() == 0)) {
            return new ResultList(Result.FALSE);
        } else if (value == Result.INVALID) {
            return new ResultList(Result.INVALID);
        } else {
            return new ResultList(Result.TRUE);
        }
    }

    public String toString() {
        return results.toString();
    }
}
