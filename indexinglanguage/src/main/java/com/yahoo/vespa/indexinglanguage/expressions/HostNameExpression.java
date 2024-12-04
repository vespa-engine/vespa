// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.StringFieldValue;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;

/**
 * @author Simon Thoresen Hult
 */
public final class HostNameExpression extends Expression {

    @Override
    public boolean requiresInput() { return false; }

    @Override
    public DataType setInputType(DataType inputType, VerificationContext context) {
        super.setInputType(inputType, context);
        return DataType.STRING;
    }

    @Override
    public DataType setOutputType(DataType outputType, VerificationContext context) {
        super.setOutputType(DataType.STRING, outputType, null, context);
        return AnyDataType.instance;
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setCurrentType(createdOutputType());
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        context.setCurrentValue(new StringFieldValue(normalizeHostName(getDefaults().vespaHostname())));
    }

    @Override
    public DataType createdOutputType() { return DataType.STRING; }

    @Override
    public String toString() { return "hostname"; }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof HostNameExpression)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    public static String normalizeHostName(String hostName) {
        int pos = hostName.indexOf('.');
        return pos < 0 ? hostName : hostName.substring(0, pos);
    }

}
