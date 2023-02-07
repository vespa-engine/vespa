// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

import com.yahoo.io.reader.NamedReader;
import static com.yahoo.config.model.test.TestUtil.joinLines;

import java.nio.charset.StandardCharsets;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.List;

import com.yahoo.yolean.Exceptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author arnej
 */
public class IntermediateCollectionTestCase {

    @Test
    void can_add_minimal_schema() throws Exception {
        String input = joinLines
                ("schema foo {",
                        "  document foo {",
                        "  }",
                        "}");
        var collection = new IntermediateCollection();
        ParsedSchema schema = collection.addSchemaFromString(input);
        assertEquals("foo", schema.name());
        assertTrue(schema.hasDocument());
        assertEquals("foo", schema.getDocument().name());
    }

    @Test
    void names_may_differ() throws Exception {
        String input = joinLines
                ("schema foo_search {",
                        "  document foo {",
                        "  }",
                        "}");
        var collection = new IntermediateCollection();
        ParsedSchema schema = collection.addSchemaFromString(input);
        assertEquals("foo_search", schema.name());
        assertTrue(schema.hasDocument());
        assertEquals("foo", schema.getDocument().name());
    }

    @Test
    void can_add_schema_files() throws Exception {
        var collection = new IntermediateCollection();
        collection.addSchemaFromFile("src/test/derived/deriver/child.sd");
        collection.addSchemaFromFile("src/test/derived/deriver/grandparent.sd");
        collection.addSchemaFromFile("src/test/derived/deriver/parent.sd");
        var schemes = collection.getParsedSchemas();
        assertEquals(schemes.size(), 3);
        var schema = schemes.get("child");
        assertTrue(schema != null);
        assertEquals(schema.name(), "child");
        schema = schemes.get("parent");
        assertTrue(schema != null);
        assertEquals(schema.name(), "parent");
        schema = schemes.get("grandparent");
        assertTrue(schema != null);
        assertEquals(schema.name(), "grandparent");
    }

    NamedReader readerOf(String fileName) throws Exception {
        File f = new File(fileName);
        FileReader fr = new FileReader(f, StandardCharsets.UTF_8);
        BufferedReader br = new BufferedReader(fr);
        return new NamedReader(fileName, br);
    }

    @Test
    void can_add_schemas() throws Exception {
        var collection = new IntermediateCollection();
        collection.addSchemaFromReader(readerOf("src/test/derived/deriver/child.sd"));
        collection.addSchemaFromReader(readerOf("src/test/derived/deriver/grandparent.sd"));
        collection.addSchemaFromReader(readerOf("src/test/derived/deriver/parent.sd"));
        var schemes = collection.getParsedSchemas();
        assertEquals(schemes.size(), 3);
        var schema = schemes.get("child");
        assertTrue(schema != null);
        assertEquals(schema.name(), "child");
        schema = schemes.get("parent");
        assertTrue(schema != null);
        assertEquals(schema.name(), "parent");
        schema = schemes.get("grandparent");
        assertTrue(schema != null);
        assertEquals(schema.name(), "grandparent");
    }

    ParsedRankProfile get(List<ParsedRankProfile> all, String name) {
        for (var rp : all) {
            if (rp.name().equals(name)) return rp;
        }
        return null;
    }

    @Test
    void can_add_extra_rank_profiles() throws Exception {
        var collection = new IntermediateCollection();
        collection.addSchemaFromFile("src/test/derived/rankprofilemodularity/test.sd");
        collection.addRankProfileFile("test", "src/test/derived/rankprofilemodularity/test/outside_schema1.profile");
        collection.addRankProfileFile("test", readerOf("src/test/derived/rankprofilemodularity/test/subdirectory/outside_schema2.profile"));
        var schemes = collection.getParsedSchemas();
        assertEquals(schemes.size(), 1);
        var schema = schemes.get("test");
        assertTrue(schema != null);
        assertEquals(schema.name(), "test");
        var rankProfiles = schema.getRankProfiles();
        assertEquals(rankProfiles.size(), 7);
        var outside = get(rankProfiles, "outside_schema1");
        assertTrue(outside != null);
        assertEquals(outside.name(), "outside_schema1");
        var functions = outside.getFunctions();
        assertEquals(functions.size(), 1);
        assertEquals(functions.get(0).name(), "fo1");
        outside = get(rankProfiles, "outside_schema2");
        assertTrue(outside != null);
        assertEquals(outside.name(), "outside_schema2");
        functions = outside.getFunctions();
        assertEquals(functions.size(), 1);
        assertEquals(functions.get(0).name(), "fo2");
    }

    @Test
    void name_mismatch_throws() throws Exception {
        var collection = new IntermediateCollection();
        var ex = assertThrows(IllegalArgumentException.class, () ->
                collection.addSchemaFromReader(readerOf("src/test/cfg/application/sdfilenametest/schemas/notmusic.sd")));
        assertEquals("The file containing schema 'music' must be named 'music.sd', but is 'notmusic.sd'",
                ex.getMessage());
    }

    @Test
    void bad_parse_throws() throws Exception {
        var collection = new IntermediateCollection();
        var ex1 = assertThrows(IllegalArgumentException.class, () ->
                collection.addSchemaFromFile("src/test/examples/badparse.sd"));
        assertTrue(Exceptions.toMessageString(ex1).startsWith("Failed parsing schema from 'src/test/examples/badparse.sd': Encountered"), ex1.getMessage());
        var ex2 = assertThrows(IllegalArgumentException.class, () ->
                collection.addSchemaFromReader(readerOf("src/test/examples/badparse.sd")));
        assertTrue(Exceptions.toMessageString(ex2).startsWith("Failed parsing schema from 'src/test/examples/badparse.sd': Encountered"), ex2.getMessage());
        collection.addSchemaFromFile("src/test/derived/rankprofilemodularity/test.sd");
        collection.addRankProfileFile("test", "src/test/derived/rankprofilemodularity/test/outside_schema1.profile");
        var ex3 = assertThrows(ParseException.class, () ->
                collection.addRankProfileFile("test", "src/test/examples/badparse.sd"));
        assertTrue(Exceptions.toMessageString(ex3).startsWith("Failed parsing rank-profile from 'src/test/examples/badparse.sd': Encountered"), ex3.getMessage());
    }

    @Test
    void can_resolve_document_inheritance() throws Exception {
        var collection = new IntermediateCollection();
        collection.addSchemaFromFile("src/test/derived/deriver/child.sd");
        collection.addSchemaFromFile("src/test/derived/deriver/grandparent.sd");
        collection.addSchemaFromFile("src/test/derived/deriver/parent.sd");
        collection.resolveInternalConnections();
        var schemes = collection.getParsedSchemas();
        assertEquals(schemes.size(), 3);
        var childDoc = schemes.get("child").getDocument();
        var inherits = childDoc.getResolvedInherits();
        assertEquals(inherits.size(), 1);
        var parentDoc = inherits.get(0);
        assertEquals(parentDoc.name(), "parent");
        inherits = parentDoc.getResolvedInherits();
        assertEquals(inherits.size(), 1);
        assertEquals(inherits.get(0).name(), "grandparent");
    }

    @Test
    void can_detect_schema_inheritance_cycles() throws Exception {
        var collection = new IntermediateCollection();
        collection.addSchemaFromString("schema foo inherits bar { document foo {} }");
        collection.addSchemaFromString("schema bar inherits qux { document bar {} }");
        collection.addSchemaFromString("schema qux inherits foo { document qux {} }");
        assertEquals(collection.getParsedSchemas().size(), 3);
        var ex = assertThrows(IllegalArgumentException.class, () ->
                collection.resolveInternalConnections());
        assertTrue(ex.getMessage().startsWith("Inheritance/reference cycle for schemas: "));
    }

    @Test
    void can_detect_document_inheritance_cycles() throws Exception {
        var collection = new IntermediateCollection();
        collection.addSchemaFromString("schema foo { document foo inherits bar {} }");
        collection.addSchemaFromString("schema bar { document bar inherits qux {} }");
        collection.addSchemaFromString("schema qux { document qux inherits foo {} }");
        assertEquals(collection.getParsedSchemas().size(), 3);
        var ex = assertThrows(IllegalArgumentException.class, () ->
                collection.resolveInternalConnections());
        System.err.println("ex: " + ex.getMessage());
        assertTrue(ex.getMessage().startsWith("Inheritance/reference cycle for documents: "));
    }

    @Test
    void can_detect_missing_doc() throws Exception {
        var collection = new IntermediateCollection();
        collection.addSchemaFromString("schema foo { document foo inherits bar {} }");
        collection.addSchemaFromString("schema qux { document qux inherits foo {} }");
        assertEquals(collection.getParsedSchemas().size(), 2);
        var ex = assertThrows(IllegalArgumentException.class, () ->
                collection.resolveInternalConnections());
        assertEquals("document foo inherits from unavailable document bar", ex.getMessage());
    }

    @Test
    void can_detect_document_reference_cycle() throws Exception {
        var collection = new IntermediateCollection();
        collection.addSchemaFromString("schema foo { document foo { field oneref type reference<bar> {} } }");
        collection.addSchemaFromString("schema bar { document bar { field tworef type reference<foo> {} } }");
        assertEquals(collection.getParsedSchemas().size(), 2);
        var ex = assertThrows(IllegalArgumentException.class, () ->
                collection.resolveInternalConnections());
        System.err.println("ex: " + ex.getMessage());
        assertTrue(ex.getMessage().startsWith("Inheritance/reference cycle for documents: "));
    }

    @Test
    void can_detect_cycles_with_reference() throws Exception {
        var collection = new IntermediateCollection();
        collection.addSchemaFromString("schema foo { document foodoc inherits bardoc {} }");
        collection.addSchemaFromString("schema bar { document bardoc { field myref type reference<qux> { } } }");
        collection.addSchemaFromString("schema qux inherits foo { document qux inherits foodoc {} }");
        assertEquals(collection.getParsedSchemas().size(), 3);
        var ex = assertThrows(IllegalArgumentException.class, () ->
                collection.resolveInternalConnections());
        System.err.println("ex: " + ex.getMessage());
        assertTrue(ex.getMessage().startsWith("Inheritance/reference cycle for documents: "));
    }


}
