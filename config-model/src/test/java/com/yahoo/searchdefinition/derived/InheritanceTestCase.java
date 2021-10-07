// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.document.DataType;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.searchdefinition.Index;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.configmodel.producers.DocumentManager;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Tests inheritance
 *
 * @author bratseth
 */
public class InheritanceTestCase extends AbstractExportingTestCase {

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    @Test
    public void requireThatIndexedStructFieldCanBeInherited() throws IOException, ParseException {
        String dir = "src/test/derived/inheritstruct/";
        SearchBuilder builder = new SearchBuilder();
        builder.importFile(dir + "parent.sd");
        builder.importFile(dir + "child.sd");
        builder.build();
        derive("inheritstruct", builder, builder.getSearch("child"));
        assertCorrectConfigFiles("inheritstruct");
    }

    @Test
    public void requireThatInheritFromNullIsCaught() throws IOException, ParseException {
        try {
            assertCorrectDeriving("inheritfromnull");
        } catch (IllegalStateException e) {
            assertEquals("Document type 'foo' not found.", e.getMessage());
        }
    }

    @Test
    public void requireThatStructTypesAreInheritedThroughDiamond() throws IOException, ParseException {
        String dir = "src/test/derived/inheritdiamond/";
        List<String> files = Arrays.asList("grandparent.sd", "mother.sd", "father.sd", "child.sd");
        File outDir = tmpDir.newFolder("out");
        for (int startIdx = 0; startIdx < files.size(); ++startIdx) {
            SearchBuilder builder = new SearchBuilder();
            for (int fileIdx = startIdx; fileIdx < startIdx + files.size(); ++fileIdx) {
                String fileName = files.get(fileIdx % files.size());
                builder.importFile(dir + fileName);
            }
            builder.build();
            DocumentmanagerConfig.Builder b = new DocumentmanagerConfig.Builder();
            DerivedConfiguration.exportDocuments(new DocumentManager().produce(builder.getModel(), b), outDir.getPath());
            DocumentmanagerConfig dc = b.build();
            assertEquals(13, dc.datatype().size());
            assertNull(structType("child.body", dc));
            DocumentmanagerConfig.Datatype.Structtype childHeader = structType("child.header", dc);
            assertEquals(childHeader.field(0).name(), "foo");
            assertEquals(childHeader.field(1).name(), "bar");
            assertEquals(childHeader.field(2).name(), "baz");
            assertEquals(childHeader.field(3).name(), "cox");
            DocumentmanagerConfig.Datatype.Documenttype child = documentType("child", dc);
            assertEquals(child.inherits(0).name(), "document");
            assertEquals(child.inherits(1).name(), "father");
            assertEquals(child.inherits(2).name(), "mother");
            DocumentmanagerConfig.Datatype.Documenttype mother = documentType("mother", dc);
            assertEquals(mother.inherits(0).name(), "grandparent");
            assertEquals(mother.inherits(1).name(), "document");
        }
    }

    private DocumentmanagerConfig.Datatype.Structtype structType(String name, DocumentmanagerConfig dc) {
        for (DocumentmanagerConfig.Datatype dt : dc.datatype()) {
            for (DocumentmanagerConfig.Datatype.Structtype st : dt.structtype()) {
                if (name.equals(st.name())) return st;
            }
        }
        return null;
    }

    private DocumentmanagerConfig.Datatype.Documenttype documentType(String name, DocumentmanagerConfig dc) {
        for (DocumentmanagerConfig.Datatype dt : dc.datatype()) {
            for (DocumentmanagerConfig.Datatype.Documenttype dot : dt.documenttype()) {
                if (name.equals(dot.name())) return dot;
            }
        }
        return null;
    }
    
    @Test
    public void requireThatStructTypesAreInheritedFromParent() throws IOException, ParseException {
        String dir = "src/test/derived/inheritfromparent/";
        SearchBuilder builder = new SearchBuilder();
        builder.importFile(dir + "parent.sd");
        builder.importFile(dir + "child.sd");
        builder.build();
        derive("inheritfromparent", builder, builder.getSearch("child"));
        assertCorrectConfigFiles("inheritfromparent");
    }

    @Test
    public void requireThatStructTypesAreInheritedFromGrandParent() throws IOException, ParseException {
        String dir = "src/test/derived/inheritfromgrandparent/";
        SearchBuilder builder = new SearchBuilder();
        builder.importFile(dir + "grandparent.sd");
        builder.importFile(dir + "parent.sd");
        builder.importFile(dir + "child.sd");
        builder.build();
        derive("inheritfromgrandparent", builder, builder.getSearch("child"));
        assertCorrectConfigFiles("inheritfromgrandparent");
    }

    @Test
    public void testInheritance() throws IOException, ParseException {
        String dir = "src/test/derived/inheritance/";
        SearchBuilder builder = new SearchBuilder();
        builder.importFile(dir + "grandparent.sd");
        builder.importFile(dir + "father.sd");
        builder.importFile(dir + "mother.sd");
        builder.importFile(dir + "child.sd");
        builder.build();
        derive("inheritance", builder, builder.getSearch("child"));
        assertCorrectConfigFiles("inheritance");
    }

    @Test
    public void testIndexSettingInheritance() {
        SDDocumentType parent = new SDDocumentType("parent");
        Search parentSearch = new Search("parent");
        parentSearch.addDocument(parent);
        SDField prefixed = parent.addField("prefixed", DataType.STRING);
        prefixed.parseIndexingScript("{ index }");
        prefixed.addIndex(new Index("prefixed", true));

        SDDocumentType child = new SDDocumentType("child");
        child.inherit(parent);
        Search childSearch = new Search("child");
        childSearch.addDocument(child);

        prefixed = (SDField)child.getField("prefixed");
        assertNotNull(prefixed);
        assertEquals(new Index("prefixed", true), childSearch.getIndex("prefixed"));
    }

}
