// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.datatypes.StringFieldValue;
import static com.yahoo.vespa.defaults.Defaults.getDefaults;

/**
 * @author Simon Thoresen Hult
 */
public class HostNameExpression extends Expression {

    @Override
    protected void doExecute(ExecutionContext ctx) {
        ctx.setValue(new StringFieldValue(normalizeHostName(getDefaults().vespaHostname())));
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setValue(createdOutputType());
    }

    @Override
    public DataType requiredInputType() {
        return null;
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
