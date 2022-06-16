// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.rule;

import com.yahoo.document.BucketId;
import com.yahoo.document.BucketIdFactory;
import com.yahoo.document.DocumentId;
import com.yahoo.document.datatypes.BoolFieldValue;
import com.yahoo.document.datatypes.FieldPathIteratorHandler;
import com.yahoo.document.datatypes.NumericFieldValue;
import com.yahoo.document.idstring.IdIdString;
import com.yahoo.document.select.BucketSet;
import com.yahoo.document.select.Context;
import com.yahoo.document.select.Result;
import com.yahoo.document.select.ResultList;
import com.yahoo.document.select.Visitor;

import java.util.regex.Pattern;

/**
 * @author Simon Thoresen Hult
 */
public class ComparisonNode implements ExpressionNode {

    // The left- and right-hand-side of this comparison.
    private ExpressionNode lhs, rhs;

    // The operator string for this.
    private String operator;

    /**
     * Constructs a new comparison node.
     *
     * @param lhs The left-hand-side of the comparison.
     * @param operator The comparison operator.
     * @param rhs The right-hand-side of the comparison.
     */
    public ComparisonNode(ExpressionNode lhs, String operator, ExpressionNode rhs) {
        this.lhs = lhs;
        this.operator = operator;
        this.rhs = rhs;
    }

    /**
     * Returns the left hand side of this comparison.
     *
     * @return The left hand side expression.
     */
    public ExpressionNode getLHS() {
        return lhs;
    }

    /**
     * Returns the comparison operator of this.
     *
     * @return The operator.
     */
    public String getOperator() {
        return operator;
    }

    /**
     * Sets the comparison operator of this.
     *
     * @param operator The operator string.
     * @return This, to allow chaining.
     */
    public ComparisonNode setOperator(String operator) {
        this.operator = operator;
        return this;
    }

    /**
     * Returns the right hand side of this comparison.
     *
     * @return The right hand side expression.
     */
    public ExpressionNode getRHS() {
        return rhs;
    }

    @Override
    public BucketSet getBucketSet(BucketIdFactory factory) {
        if (operator.equals("==") || operator.equals("=")) {
            if (lhs instanceof IdNode && rhs instanceof LiteralNode) {
                return compare(factory, (IdNode)lhs, (LiteralNode)rhs, operator);
            } else if (rhs instanceof IdNode && lhs instanceof LiteralNode) {
                return compare(factory, (IdNode)rhs, (LiteralNode)lhs, operator);
            }
        }
        return null;
    }

    private BucketSet compare(BucketIdFactory factory, IdNode id, LiteralNode literal, String operator) {
        String field = id.getField();
        Object value = literal.getValue();
        if (field == null) {
            if (value instanceof String) {
                String name = (String)value;
                if ((operator.equals("=") && name.contains("*")) ||
                    (operator.equals("=~") && ((name.contains("*") || name.contains("?")))))
                    {
                        return null; // no idea
                    }
                return new BucketSet(factory.getBucketId(new DocumentId(name)));
            }
        } else if (field.equalsIgnoreCase("user")) {
            if (value instanceof Long) {
                return new BucketSet(new BucketId(factory.getLocationBitCount(), (Long)value));
            }
        } else if (field.equalsIgnoreCase("group")) {
            if (value instanceof String) {
                String name = (String)value;
                if ((operator.equals("=") && name.contains("*")) ||
                    (operator.equals("=~") && ((name.contains("*") || name.contains("?")))))
                    {
                        return null; // no idea
                    }
                return new BucketSet(new BucketId(factory.getLocationBitCount(), IdIdString.makeLocation(name)));
            }
        } else if (field.equalsIgnoreCase("bucket")) {
            if (value instanceof Long) {
                return new BucketSet(new BucketId((Long)value));
            }
        }
        return null;
    }

    @Override
    public Object evaluate(Context context) {
        Object oLeft = lhs.evaluate(context);
        Object oRight = rhs.evaluate(context);
        if (oLeft == null || oRight == null) {
            return evaluateWithAtLeastOneNullSide(oLeft, oRight);
        }
        if (oLeft == Result.INVALID || oRight == Result.INVALID) {
            return new ResultList(Result.INVALID);
        }
        if (oLeft instanceof AttributeNode.VariableValueList && oRight instanceof AttributeNode.VariableValueList) {
            if (operator.equals("==")) {
                return evaluateListsTrue((AttributeNode.VariableValueList)oLeft, (AttributeNode.VariableValueList)oRight);
            } else if (operator.equals("!=")) {
                return evaluateListsFalse((AttributeNode.VariableValueList)oLeft, (AttributeNode.VariableValueList)oRight);
            } else {
                return new ResultList(Result.INVALID);
            }
        } else if (oLeft instanceof AttributeNode.VariableValueList) {
            return evaluateLhsListAndRhsSingle((AttributeNode.VariableValueList)oLeft, oRight);
        } else if (oRight instanceof AttributeNode.VariableValueList) {
            return evaluateLhsSingleAndRhsList(oLeft, (AttributeNode.VariableValueList)oRight);
        }
        return new ResultList(evaluateBool(oLeft, oRight));
    }

    /**
     * Evaluates a binary comparison where one or both operands are null.
     * Boolean outcomes are only defined for (in)equality relations, all others
     * return Result.INVALID.
     *
     * Precondition: lhs AND/OR rhs is null.
     */
    private ResultList evaluateWithAtLeastOneNullSide(Object lhs, Object rhs) {
        if (operator.equals("==") || operator.equals("=")) { // Glob (=) operator falls back to equality for non-strings
            return ResultList.fromBoolean(lhs == rhs);
        } else if (operator.equals("!=")) {
            return ResultList.fromBoolean(lhs != rhs);
        } else {
            return new ResultList(Result.INVALID);
        }
    }

    private ResultList evaluateListsTrue(AttributeNode.VariableValueList lhs, AttributeNode.VariableValueList rhs) {
        if (lhs.size() != rhs.size()) {
            return new ResultList(Result.FALSE);
        }

        for (int i = 0; i < lhs.size(); i++) {
            if (!lhs.get(i).getVariables().equals(rhs.get(i).getVariables())) {
                return new ResultList(Result.FALSE);
            }

            if (evaluateEquals(lhs.get(i).getValue(), rhs.get(i).getValue()) == Result.FALSE) {
                return new ResultList(Result.FALSE);
            }
        }

        return new ResultList(Result.TRUE);
    }

    private ResultList evaluateListsFalse(AttributeNode.VariableValueList lhs, AttributeNode.VariableValueList rhs) {
        ResultList lst = evaluateListsTrue(lhs, rhs);
        if (lst.toResult() == Result.TRUE) {
            return new ResultList(Result.FALSE);
        } else if (lst.toResult() == Result.FALSE) {
            return new ResultList(Result.TRUE);
        } else {
            return lst;
        }
    }

    private ResultList evaluateLhsListAndRhsSingle(AttributeNode.VariableValueList lhs, Object rhs) {
        if (rhs == null && lhs == null) {
            return new ResultList(Result.TRUE);
        }

        if (rhs == null || lhs == null) {
            return new ResultList(Result.FALSE);
        }

        ResultList retVal = new ResultList();
        for (ResultList.VariableValue value : lhs) {
            Result result = evaluateBool(value.getValue(), rhs);
            retVal.add((FieldPathIteratorHandler.VariableMap)value.getVariables().clone(), result);
        }

        return retVal;
    }

    private ResultList evaluateLhsSingleAndRhsList(Object lhs, AttributeNode.VariableValueList rhs) {
        if (rhs == null && lhs == null) {
            return new ResultList(Result.TRUE);
        }

        if (rhs == null || lhs == null) {
            return new ResultList(Result.FALSE);
        }

        ResultList retVal = new ResultList();
        for (ResultList.VariableValue value : rhs) {
            Result result = evaluateBool(lhs, value.getValue());
            retVal.add((FieldPathIteratorHandler.VariableMap)value.getVariables().clone(), result);
        }

        return retVal;
    }

    /**
     * Evaluate this expression on two operands, given that they are not invalid.
     *
     * @param lhs Left hand side of operation.
     * @param rhs Right hand side of operation.
     * @return The evaluation result.
     */
    private Result evaluateBool(Object lhs, Object rhs) {
        if (operator.equals("==")) {
            return evaluateEquals(lhs, rhs);
        } else if (operator.equals("!=")) {
            return Result.invert(evaluateEquals(lhs, rhs));
        } else if (operator.equals("<") || operator.equals("<=") ||
                   operator.equals(">") || operator.equals(">=")) {
            return evaluateNumber(lhs, rhs);
        } else if (operator.equals("=~") || operator.equals("=")) {
            return evaluateString(lhs, rhs);
        }
        throw new IllegalStateException("Comparison operator '" + operator + "' is not supported.");
    }

    /**
     * Compare two operands for equality.
     *
     * @param lhs Left hand side of operation.
     * @param rhs Right hand side of operation.
     * @return Wether or not the two operands are equal.
     */
    private Result evaluateEquals(Object lhs, Object rhs) {
        if (lhs == null || rhs == null) {
            return Result.toResult(lhs == rhs);
        }

        double a = getAsNumber(lhs);
        double b = getAsNumber(rhs);
        if (Double.isNaN(a) || Double.isNaN(b)) {
            return Result.toResult(lhs.toString().equals(rhs.toString()));
        }
        return Result.toResult(a == b); // Ugh, comparing doubles? Should be converted to long value perhaps...
    }

    private double getAsNumber(Object value) {
        if (value instanceof Number) {
            return ((Number)value).doubleValue();
        } else if (value instanceof NumericFieldValue) {
            return getAsNumber(((NumericFieldValue)value).getNumber());
        } else if (value instanceof BoolFieldValue) {
            return ((BoolFieldValue)value).getBoolean() ? 1 : 0;
        } else if (value instanceof Boolean) {
            return (Boolean)value ? 1 : 0;
        } else {
            return Double.NaN; //new IllegalStateException("Term '" + value + "' (" + value.getClass() + ") does not evaluate to a number.");
        }
    }

    /**
     * Evalutes the value of this term over a document, given that both operands must be numbers.
     *
     * @param lhs Left hand side of operation.
     * @param rhs Right hand side of operation.
     * @return The evaluation result.
     */
    private Result evaluateNumber(Object lhs, Object rhs) {
        double a = getAsNumber(lhs);
        double b = getAsNumber(rhs);
        if (Double.isNaN(a) || Double.isNaN(b)) {
            return Result.INVALID;
        }
        if (operator.equals("<")) {
            return Result.toResult(a < b);
        } else if (operator.equals("<=")) {
            return Result.toResult(a <= b);
        } else if (operator.equals(">")) {
            return Result.toResult(a > b);
        } else {
            return Result.toResult(a >= b);
        }
    }

    /**
     * Evalutes the value of this term over a document, given that both operands must be strings.
     *
     * @param lhs Left hand side of operation.
     * @param rhs Right hand side of operation.
     * @return The evaluation result.
     */
    private Result evaluateString(Object lhs, Object rhs) {
        String left = "" + lhs; // Allows null objects to evaluate to string.
        String right = "" + rhs;
        if (operator.equals("=~")) {
            return Result.toResult(Pattern.compile(right).matcher(left).find());
        } else {
            return Result.toResult(Pattern.compile(globToRegex(right)).matcher(left).find());
        }
    }

    /**
     * Converts a glob pattern to a corresponding regular expression string.
     *
     * @param glob The glob pattern.
     * @return The regex string.
     */
    private String globToRegex(String glob) {
        StringBuilder ret = new StringBuilder();
        ret.append("^");
        for (int i = 0; i < glob.length(); i++) {
            ret.append(globToRegex(glob.charAt(i)));
        }
        ret.append("$");

        return ret.toString();
    }

    /**
     * Converts a single character in a glob expression to the corresponding regular expression string.
     *
     * @param glob The glob character.
     * @return The regex string.
     */
    private String globToRegex(char glob) {
        switch (glob) {
        case'*':
            return ".*";
        case'?':
            return ".";
        case'^':
        case'$':
        case'|':
        case'{':
        case'}':
        case'(':
        case')':
        case'[':
        case']':
        case'\\':
        case'+':
        case'.':
            return "\\" + glob;
        default:
            return "" + glob;
        }
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return lhs + " " + operator + " " + rhs;
    }
}
