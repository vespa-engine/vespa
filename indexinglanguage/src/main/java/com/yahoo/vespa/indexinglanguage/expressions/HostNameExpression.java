// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.StringFieldValue;
import static com.yahoo.vespa.defaults.Defaults.getDefaults;

/**
 * @author Simon Thoresen Hult
 */
public final class HostNameExpression extends Expression {

    public HostNameExpression() {
        super(null);
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        context.setValue(new StringFieldValue(normalizeHostName(getDefaults().vespaHostname())));
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setValueType(createdOutputType());
    }

    @Override
    public DataType createdOutputType() {
        return DataType.STRING;
    }

    @Override
    public String toString() {
        return "hostname";
    }

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
