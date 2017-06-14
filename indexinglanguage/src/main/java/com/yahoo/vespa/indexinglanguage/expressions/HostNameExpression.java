// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.datatypes.StringFieldValue;

import java.net.InetAddress;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class HostNameExpression extends Expression {

    @Override
    protected void doExecute(ExecutionContext ctx) {
        try {
            ctx.setValue(new StringFieldValue(normalizeHostName(InetAddress.getLocalHost().getHostName())));
        } catch (java.net.UnknownHostException e) {
            throw new RuntimeException(e);
        }
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
