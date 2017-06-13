// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.vespa.indexinglanguage.SimpleTestAdapter;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static com.yahoo.vespa.indexinglanguage.expressions.ExpressionAssert.assertVerify;
import static org.junit.Assert.*;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class HostNameTestCase {

    @Test
    public void requireThatHashCodeAndEqualsAreImplemented() {
        Expression exp = new HostNameExpression();
        assertFalse(exp.equals(new Object()));
        assertEquals(exp, new HostNameExpression());
        assertEquals(exp.hashCode(), new HostNameExpression().hashCode());
    }

    @Test
    public void requireThatExpressionCanBeVerified() {
        Expression exp = new HostNameExpression();
        assertVerify(null, exp, DataType.STRING);
        assertVerify(DataType.INT, exp, DataType.STRING);
        assertVerify(DataType.STRING, exp, DataType.STRING);
    }

    @Test
    public void requireThatHostnameIsSet() throws UnknownHostException {
        ExecutionContext ctx = new ExecutionContext(new SimpleTestAdapter());
        new HostNameExpression().execute(ctx);

        FieldValue val = ctx.getValue();
        assertTrue(val instanceof StringFieldValue);
        assertEquals(HostNameExpression.normalizeHostName(InetAddress.getLocalHost().getHostName()),
                     ((StringFieldValue)val).getString());
    }
}
