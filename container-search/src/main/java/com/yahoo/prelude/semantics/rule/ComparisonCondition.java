// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.rule;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.yahoo.prelude.semantics.engine.Choicepoint;
import com.yahoo.prelude.semantics.engine.RuleEvaluation;

/**
 * A condition which is true of the <i>values</i> of its two subconditions are true
 * and both have the same value
 *
 * @author bratseth
 */
public class ComparisonCondition extends CompositeCondition {

    private Operator operator;

    public ComparisonCondition(Condition leftCondition,String operatorString,Condition rightCondition) {
        operator=Operator.get(operatorString);
        addCondition(leftCondition);
        addCondition(rightCondition);
    }

    protected boolean doesMatch(RuleEvaluation evaluation) {
        Object left=null;
        Object right=null;
        boolean matches=false;
        Choicepoint  choicepoint=evaluation.getChoicepoint(this,true);
        try {
            matches=getLeftCondition().matches(evaluation);
            if (!matches) return false;

            left=evaluation.getValue();
            evaluation.setValue(null);

            choicepoint.backtrackPosition();
            matches=getRightCondition().matches(evaluation);
            if (!matches) return false;

            right=evaluation.getValue();
            evaluation.setValue(right);
            matches=operator.compare(left,right);
            return matches;
        }
        finally {
            if (!matches)
                choicepoint.backtrack();
            traceResult(matches,evaluation,left,right);
        }
    }

    protected void traceResult(boolean matches,RuleEvaluation e) {
        // Uses our own logging method instead
    }

    protected void traceResult(boolean matches,RuleEvaluation e,Object left,Object right) {
        if (matches && e.getTraceLevel()>=3)
            e.trace(3,"Matched '" + this + "'" + getMatchInfoString(e) + " at " + e.previousItem() + " as " + left + operator + right + " is true");
        if (!matches && e.getTraceLevel()>=3)
            e.trace(3,"Did not match '" + this + "' at " + e.currentItem() + " as " + left + operator + right + " is false");
    }

    public Condition getLeftCondition() {
        return getCondition(0);
    }

    public void setLeftCondition(Condition leftCondition) {
        setCondition(0,leftCondition);
    }

    public Condition getRightCondition() {
        return getCondition(1);
    }

    public void setRightCondition(Condition rightCondition) {
        setCondition(1,rightCondition);
    }

    protected String toInnerString() {
        return toInnerString(operator.toString());
    }

    private static final class Operator {

        private String operatorString;

        private static Map<String, Operator> operators=new HashMap<>();

        public static final Operator equals=new Operator("=");
        public static final Operator largerequals=new Operator(">=");
        public static final Operator smallerequals=new Operator("<=");
        public static final Operator larger=new Operator(">");
        public static final Operator smaller=new Operator("<");
        public static final Operator different=new Operator("!=");
        public static final Operator contains=new Operator("=~");

        private Operator(String operator) {
            this.operatorString=operator;
            operators.put(operatorString,this);
        }

        private static Operator get(String operatorString) {
            Operator operator=operators.get(operatorString);
            if (operator==null)
                throw new IllegalArgumentException("Unknown operator '" + operatorString + "'");
            return operator;
        }

        public boolean compare(Object left,Object right) {
            if (this==equals)
                return equals(left,right);
            if (this==different)
                return !equals(left,right);

            if (left==null || right==null) return false;

            if (this==contains)
                return contains(left,right);
            if (this==largerequals)
                return larger(left,right) || equals(left,right);
            if (this==smallerequals)
                return !larger(left,right);
            if (this==larger)
                return larger(left,right);
            if (this==smaller)
                return !larger(left,right) && !equals(left,right);
            throw new RuntimeException("Programming error, fix this method");
        }

        private boolean equals(Object left,Object right) {
            if (left==null && right==null) return true;
            if (left==null) return false;
            return left.equals(right);
        }

        /** True if left contains right */
        private boolean contains(Object left,Object right) {
            if (left instanceof Collection)
                return ((Collection<?>)left).contains(right);
            else
                return left.toString().indexOf(right.toString())>=0;
        }

        /** true if left is larger than right */
        private boolean larger(Object left,Object right) {
            if ((left instanceof Number) && (right instanceof Number))
                return ((Number)left).doubleValue()>((Number)right).doubleValue();
            else
                return left.toString().compareTo(right.toString())>0;
        }

        public int hashCode() {
            return operatorString.hashCode();
        }

        public boolean equals(Object other) {
            if ( ! (other instanceof Operator)) return false;
            return other.toString().equals(this.toString());
        }

        public String toString() {
            return operatorString;
        }

    }

}
