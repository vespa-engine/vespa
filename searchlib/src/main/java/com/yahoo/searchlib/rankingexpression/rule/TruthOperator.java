// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.rankingexpression.rule;

import java.io.Serializable;

/**
 * A mathematical operator
 *
 * @author bratseth
 */
public enum TruthOperator  implements Serializable {

    SMALLER("<")   { public boolean evaluate(double x, double y) { return x<y; } },
    SMALLEREQUAL("<=")  { public boolean evaluate(double x, double y) { return x<=y; } },
    EQUAL("==")  { public boolean evaluate(double x, double y) { return x==y; } },
    APPROX_EQUAL("~=") { public boolean evaluate(double x, double y) { return approxEqual(x,y); } },
    LARGER(">")   { public boolean evaluate(double x, double y) { return x>y; } },
    LARGEREQUAL(">=")  { public boolean evaluate(double x, double y) { return x>=y; } },
    NOTEQUAL("!=")  { public boolean evaluate(double x, double y) { return x!=y; } };

    private final String operatorString;

    TruthOperator(String operatorString) {
        this.operatorString=operatorString;
    }

    /** Perform the truth operation on the input */
    public abstract boolean evaluate(double x, double y);

    @Override
    public String toString() { return operatorString; }

    public static TruthOperator fromString(String string) {
        for (TruthOperator operator : values())
            if (operator.toString().equals(string))
                return operator;
        throw new IllegalArgumentException("Illegal truth operator '" + string + "'");
    }

    private static boolean approxEqual(double x,double y) {
        if (y < -1.0 || y > 1.0) {
            x = Math.nextAfter(x/y, 1.0);
            y = 1.0;
        } else {
            x = Math.nextAfter(x, y);
        }
        return x==y;
    }

}
