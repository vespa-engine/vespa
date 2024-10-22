package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.NumericFieldValue;

/**
 * Utility expression that will sleep the amount of time given in the numeric field.
 * Non-numeric fields will be ignored
 * @author baldersheim
 */
public final class SleepExpression extends Expression {
    public SleepExpression() {
        super(UnresolvedDataType.INSTANCE);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        FieldValue value = context.getValue();
        if (value instanceof NumericFieldValue num) {
            double napSecs = num.getNumber().doubleValue();
            long nanos = (long)(napSecs*1_000_000_000.0);
            try {
                Thread.sleep(nanos / 1_000_000, (int) (nanos % 1_000_000));
            } catch (InterruptedException e) {
                // Do nothing
            }
        }
    }

    @Override protected void doVerify(VerificationContext context) { }
    @Override public DataType createdOutputType() { return null; }
    @Override public String toString() { return "sleep"; }
    @Override public boolean equals(Object obj) { return obj instanceof SleepExpression; }
    @Override public int hashCode() { return getClass().hashCode(); }
}
