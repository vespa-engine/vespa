// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;


import com.yahoo.schema.ApplicationBuilder;

import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests struct inheritance
 *
 * @author arnej
 */
public class StructInheritanceTestCase extends AbstractExportingTestCase {

    @TempDir
    public File tmpDir;

    @Test
    void requireThatStructCanInherit() throws IOException, ParseException {
        String dir = "src/test/derived/structinheritance/";
        ApplicationBuilder builder = new ApplicationBuilder();
        builder.addSchemaFile(dir + "simple.sd");
        builder.build(false);
        derive("structinheritance", builder, builder.getSchema("simple"));
        assertCorrectConfigFiles("structinheritance");
    }

    @Test
    void requireThatRedeclareIsNotAllowed() throws IOException, ParseException {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            String dir = "src/test/derived/structinheritance/";
            ApplicationBuilder builder = new ApplicationBuilder();
            builder.addSchemaFile(dir + "bad.sd");
            builder.build(true);
            derive("structinheritance", builder, builder.getSchema("bad"));
        });
        assertTrue(exception.getMessage().contains("cannot inherit from base and redeclare field name"));
    }

}
