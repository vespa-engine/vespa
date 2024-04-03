package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.NumericFieldValue;

/**
 * Utility expression that will sleep the amount of time given in the numeric field.
 * Non-numeric fields will be ignored
 * @author baldersheim
 */
public final class BusyWaitExpression extends Expression {
    public BusyWaitExpression() {
        super(UnresolvedDataType.INSTANCE);
    }

    private static double nihlakanta(int i) {
        long a = 2 + i * 4L;
        return (24 * (a+2))/(double)(a*(a+1)*(a+2)*(a+3));
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        FieldValue value = context.getValue();
        if (value instanceof NumericFieldValue num) {
            double napSecs = num.getNumber().doubleValue();
            long doom = System.nanoTime() + (long)(1_000_000_000.0 * napSecs);
            while (doom > System.nanoTime()) {
                double pi = 3;
                for (int i = 0; i < 1000; i++) {
                    pi += nihlakanta(i);
                }
                context.getCache().put("Busy wait computing pi and store it to avoid jit optiming it away", pi);
            }
        }
    }

    @Override protected void doVerify(VerificationContext context) { }
    @Override public DataType createdOutputType() { return null; }
    @Override public String toString() { return "sleep"; }
    @Override public boolean equals(Object obj) { return obj instanceof BusyWaitExpression; }
    @Override public int hashCode() { return getClass().hashCode(); }
}
