// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.document.DataType;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.searchdefinition.Index;
import com.yahoo.searchdefinition.Schema;
import com.yahoo.searchdefinition.SchemaBuilder;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.configmodel.producers.DocumentManager;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Tests struct inheritance
 *
 * @author arnej
 */
public class StructInheritanceTestCase extends AbstractExportingTestCase {

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    @Rule
    public final ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void requireThatStructCanInherit() throws IOException, ParseException {
        String dir = "src/test/derived/structinheritance/";
        SchemaBuilder builder = new SchemaBuilder();
        builder.importFile(dir + "simple.sd");
        builder.build(false);
        derive("structinheritance", builder, builder.getSchema("simple"));
        assertCorrectConfigFiles("structinheritance");
    }

    @Test
    public void requireThatRedeclareIsNotAllowed() throws IOException, ParseException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("cannot inherit from base and redeclare field name");
        String dir = "src/test/derived/structinheritance/";
        SchemaBuilder builder = new SchemaBuilder();
        builder.importFile(dir + "bad.sd");
        builder.build();
        derive("structinheritance", builder, builder.getSchema("bad"));
    }

}
