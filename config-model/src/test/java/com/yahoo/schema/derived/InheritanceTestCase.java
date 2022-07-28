// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.document.DataType;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.schema.Index;
import com.yahoo.schema.Schema;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.vespa.configmodel.producers.DocumentManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests inheritance
 *
 * @author bratseth
 */
public class InheritanceTestCase extends AbstractExportingTestCase {

    @TempDir
    public File tmpDir;

    @Test
    void requireThatIndexedStructFieldCanBeInherited() throws IOException, ParseException {
        String dir = "src/test/derived/inheritstruct/";
        ApplicationBuilder builder = new ApplicationBuilder();
        builder.addSchemaFile(dir + "parent.sd");
        builder.addSchemaFile(dir + "child.sd");
        builder.build(true);
        derive("inheritstruct", builder, builder.getSchema("child"));
        assertCorrectConfigFiles("inheritstruct");
    }

    @Test
    void requireThatInheritFromNullIsCaught() throws IOException, ParseException {
        try {
            assertCorrectDeriving("inheritfromnull");
        } catch (IllegalArgumentException e) {
            assertEquals("document inheritfromnull inherits from unavailable document foo", e.getMessage());
        }
    }

    @Test
    void requireThatStructTypesAreInheritedThroughDiamond() throws IOException, ParseException {
        String dir = "src/test/derived/inheritdiamond/";
        {
            ApplicationBuilder builder = new ApplicationBuilder();
            builder.addSchemaFile(dir + "grandparent.sd");
            builder.addSchemaFile(dir + "mother.sd");
            builder.addSchemaFile(dir + "father.sd");
            builder.addSchemaFile(dir + "child.sd");
            builder.build(true);
            derive("inheritdiamond", builder, builder.getSchema("child"));
            assertCorrectConfigFiles("inheritdiamond");
        }
        List<String> files = Arrays.asList("grandparent.sd", "mother.sd", "father.sd", "child.sd");
        File outDir = newFolder(tmpDir, "out");
        for (int startIdx = 0; startIdx < files.size(); ++startIdx) {
            var builder = new ApplicationBuilder(new TestProperties());
            for (int fileIdx = startIdx; fileIdx < startIdx + files.size(); ++fileIdx) {
                String fileName = files.get(fileIdx % files.size());
                builder.addSchemaFile(dir + fileName);
            }
            builder.build(true);
            DocumentmanagerConfig.Builder b = new DocumentmanagerConfig.Builder();
            DerivedConfiguration.exportDocuments(new DocumentManager().
                    produce(builder.getModel(), b), outDir.getPath());
            DocumentmanagerConfig dc = b.build();
            assertEquals(5, dc.doctype().size());

            assertNull(structType("child.body", dc));
            var childHeader = structType("child.header", dc);
            assertEquals(childHeader.field(0).name(), "foo");
            assertEquals(childHeader.field(1).name(), "bar");
            assertEquals(childHeader.field(2).name(), "baz");
            assertEquals(childHeader.field(3).name(), "cox");

            var root = documentType("document", dc);
            var child = documentType("child", dc);
            var mother = documentType("mother", dc);
            var father = documentType("father", dc);
            var grandparent = documentType("grandparent", dc);

            assertEquals(child.inherits(0).idx(), root.idx());
            assertEquals(child.inherits(1).idx(), mother.idx());
            assertEquals(child.inherits(2).idx(), father.idx());
            assertEquals(mother.inherits(0).idx(), root.idx());
            assertEquals(mother.inherits(1).idx(), grandparent.idx());
        }
    }

    private DocumentmanagerConfig.Doctype.Structtype structType(String name, DocumentmanagerConfig dc) {
        for (var dt : dc.doctype()) {
            for (var st : dt.structtype()) {
                if (name.equals(st.name())) return st;
            }
        }
        return null;
    }

    private DocumentmanagerConfig.Doctype documentType(String name, DocumentmanagerConfig dc) {
        for (var dot : dc.doctype()) {
            if (name.equals(dot.name())) return dot;
        }
        return null;
    }

    @Test
    void requireThatStructTypesAreInheritedFromParent() throws IOException, ParseException {
        String dir = "src/test/derived/inheritfromparent/";
        ApplicationBuilder builder = new ApplicationBuilder();
        builder.addSchemaFile(dir + "parent.sd");
        builder.addSchemaFile(dir + "child.sd");
        builder.build(true);
        derive("inheritfromparent", builder, builder.getSchema("child"));
        assertCorrectConfigFiles("inheritfromparent");
    }

    @Test
    void requireThatStructTypesAreInheritedFromGrandParent() throws IOException, ParseException {
        String dir = "src/test/derived/inheritfromgrandparent/";
        ApplicationBuilder builder = new ApplicationBuilder();
        builder.addSchemaFile(dir + "grandparent.sd");
        builder.addSchemaFile(dir + "parent.sd");
        builder.addSchemaFile(dir + "child.sd");
        builder.build(true);
        derive("inheritfromgrandparent", builder, builder.getSchema("child"));
        assertCorrectConfigFiles("inheritfromgrandparent");
    }

    @Test
    void testInheritance() throws IOException, ParseException {
        String dir = "src/test/derived/inheritance/";
        ApplicationBuilder builder = new ApplicationBuilder();
        builder.addSchemaFile(dir + "grandparent.sd");
        builder.addSchemaFile(dir + "father.sd");
        builder.addSchemaFile(dir + "mother.sd");
        builder.addSchemaFile(dir + "child.sd");
        builder.build(true);
        derive("inheritance", builder, builder.getSchema("child"));
        assertCorrectConfigFiles("inheritance");
    }

    @Test
    void testIndexSettingInheritance() {
        SDDocumentType parent = new SDDocumentType("parent");
        Schema parentSchema = new Schema("parent", MockApplicationPackage.createEmpty());
        parentSchema.addDocument(parent);
        SDField prefixed = parent.addField("prefixed", DataType.STRING);
        prefixed.parseIndexingScript("{ index }");
        prefixed.addIndex(new Index("prefixed", true));

        SDDocumentType child = new SDDocumentType("child");
        child.inherit(parent);
        Schema childSchema = new Schema("child", MockApplicationPackage.createEmpty());
        childSchema.addDocument(child);

        prefixed = (SDField) child.getField("prefixed");
        assertNotNull(prefixed);
        assertEquals(new Index("prefixed", true), childSchema.getIndex("prefixed"));
    }

    @Test
    void testInheritStructDiamondNew() throws IOException, ParseException {
        String dir = "src/test/derived/declstruct/";
        List<String> files = Arrays.asList("common.sd", "foo.sd", "bar.sd", "foobar.sd");
        var builder = new ApplicationBuilder(new TestProperties());
        for (String fileName : files) {
            builder.addSchemaFile(dir + fileName);
        }
        builder.build(true);
        derive("declstruct", builder, builder.getSchema("foobar"));
        assertCorrectConfigFiles("declstruct");
    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }

}
