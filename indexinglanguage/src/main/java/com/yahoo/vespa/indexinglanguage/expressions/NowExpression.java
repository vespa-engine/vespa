// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.LongFieldValue;

/**
 * @author Simon Thoresen Hult
 */
public final class NowExpression extends Expression {

    private final Timer timer;

    public NowExpression() {
        this(SystemTimer.INSTANCE);
    }

    public NowExpression(Timer timer) {
        this.timer = timer;
    }

    @Override
    public boolean requiresInput() { return false; }

    public Timer getTimer() { return timer; }

    @Override
    public DataType setInputType(DataType inputType, VerificationContext context) {
        super.setInputType(inputType, context);
        return DataType.LONG;
    }

    @Override
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        super.setOutputType(DataType.LONG, outputType, null, context);
        return AnyDataType.instance;
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setCurrentType(createdOutputType());
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        context.setCurrentValue(new LongFieldValue(timer.currentTimeSeconds()));
    }

    @Override
    public DataType createdOutputType() { return DataType.LONG; }

    @Override
    public String toString() { return "now"; }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof NowExpression rhs)) return false;
        if (timer != rhs.timer) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + timer.hashCode();
    }

    public interface Timer {

        long currentTimeSeconds();

    }

    private static class SystemTimer implements Timer {

        static final Timer INSTANCE = new SystemTimer();

        @Override
        public long currentTimeSeconds() {
            return System.currentTimeMillis() / 1000;
        }

    }

}
