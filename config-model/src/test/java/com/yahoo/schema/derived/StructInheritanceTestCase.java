// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;


import com.yahoo.schema.ApplicationBuilder;

import com.yahoo.schema.parser.ParseException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;


import org.junit.rules.TemporaryFolder;

/**
 * Tests struct inheritance
 *
 * @author arnej
 */
public class StructInheritanceTestCase extends AbstractExportingTestCase {

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    @SuppressWarnings("deprecation")
    @Rule
    public final ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void requireThatStructCanInherit() throws IOException, ParseException {
        String dir = "src/test/derived/structinheritance/";
        ApplicationBuilder builder = new ApplicationBuilder();
        builder.addSchemaFile(dir + "simple.sd");
        builder.build(false);
        derive("structinheritance", builder, builder.getSchema("simple"));
        assertCorrectConfigFiles("structinheritance");
    }

    @Test
    public void requireThatRedeclareIsNotAllowed() throws IOException, ParseException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("cannot inherit from base and redeclare field name");
        String dir = "src/test/derived/structinheritance/";
        ApplicationBuilder builder = new ApplicationBuilder();
        builder.addSchemaFile(dir + "bad.sd");
        builder.build(true);
        derive("structinheritance", builder, builder.getSchema("bad"));
    }

}
