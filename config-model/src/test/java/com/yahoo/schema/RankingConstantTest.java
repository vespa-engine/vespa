// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author gjoranv
 */
public class RankingConstantTest {

    @Test
    void tensor_constant_properties_are_set() throws Exception {
        final String TENSOR_NAME = "my_global_tensor";
        final String TENSOR_FILE = "path/my-tensor-file.json";
        final String TENSOR_TYPE = "tensor(x{})";
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder schemaBuilder = new ApplicationBuilder(rankProfileRegistry);
        schemaBuilder.addSchema(joinLines(
                "schema test {",
                "  document test { }",
                "  rank-profile my_rank_profile {",
                "    first-phase {",
                "      expression: sum(constant(my_global_tensor))",
                "    }",
                "  }",
                "  constant " + TENSOR_NAME + " {",
                "    file: " + TENSOR_FILE,
                "    type: " + TENSOR_TYPE,
                "  }",
                "}"
        ));
        schemaBuilder.build(true);
        Schema schema = schemaBuilder.getSchema();

        Iterator<RankProfile.Constant> constantIterator = schema.constants().values().iterator();
        RankProfile.Constant constant = constantIterator.next();
        assertEquals(TENSOR_NAME, constant.name().simpleArgument().get());
        assertEquals(TENSOR_FILE, constant.valuePath().get());
        assertEquals(TENSOR_TYPE, constant.type().toString());
        assertEquals(DistributableResource.PathType.FILE, constant.pathType().get());

        assertFalse(constantIterator.hasNext());
    }

    @Test
    void tensor_constant_must_have_a_type() throws Exception {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
            ApplicationBuilder schemaBuilder = new ApplicationBuilder(rankProfileRegistry);
            schemaBuilder.addSchema(joinLines(
                    "schema test {",
                    "  document test { }",
                    "  constant foo {",
                    "    file: bar.baz",
                    "  }",
                    "}"
            ));
        });
        assertTrue(exception.getMessage().contains("must have a type"));
    }

    @Test
    void tensor_constant_must_have_a_file() throws Exception {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
            ApplicationBuilder schemaBuilder = new ApplicationBuilder(rankProfileRegistry);
            schemaBuilder.addSchema(joinLines(
                    "schema test {",
                    "  document test { }",
                    "  constant foo {",
                    "    type: tensor(x[])",
                    "  }",
                    "}"
            ));
        });
        assertTrue(exception.getMessage().contains("must have a file"));
    }

    @Test
    void constant_file_does_not_need_path_or_ending() throws Exception {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder schemaBuilder = new ApplicationBuilder(rankProfileRegistry);
        schemaBuilder.addSchema(joinLines(
                "schema test {",
                "  document test { }",
                "  constant foo {",
                "    type: tensor(x{})",
                "    file: simplename",
                "  }",
                "}"
        ));
        schemaBuilder.build(true);
        Schema schema = schemaBuilder.getSchema();
        RankProfile.Constant constant = schema.constants().values().iterator().next();
        assertEquals("simplename", constant.valuePath().get());
    }

    @Test
    void constant_uri_is_allowed() throws Exception {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder schemaBuilder = new ApplicationBuilder(rankProfileRegistry);
        schemaBuilder.addSchema(joinLines(
                "schema test {",
                "  document test { }",
                "  constant foo {",
                "    type: tensor(x{})",
                "    uri: http://somewhere.far.away/in/another-galaxy",
                "  }",
                "}"
        ));
        schemaBuilder.build(true);
        Schema schema = schemaBuilder.getSchema();
        RankProfile.Constant constant = schema.constants().values().iterator().next();
        assertEquals(DistributableResource.PathType.URI, constant.pathType().get());
        assertEquals("http://somewhere.far.away/in/another-galaxy", constant.valuePath().get());
    }

    @Test
    void constant_https_uri_is_allowed() throws Exception {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder schemaBuilder = new ApplicationBuilder(rankProfileRegistry);
        schemaBuilder.addSchema(joinLines(
                "schema test {",
                "  document test { }",
                "  constant foo {",
                "    type: tensor(x{})",
                "    uri: https://somewhere.far.away:4443/in/another-galaxy",
                "  }",
                "}"
        ));
        schemaBuilder.build(true);
        Schema schema = schemaBuilder.getSchema();
        RankProfile.Constant constant = schema.constants().values().iterator().next();
        assertEquals(DistributableResource.PathType.URI, constant.pathType().get());
        assertEquals("https://somewhere.far.away:4443/in/another-galaxy", constant.valuePath().get());
    }

    @Test
    void constant_uri_with_port_is_allowed() throws Exception {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder schemaBuilder = new ApplicationBuilder(rankProfileRegistry);
        schemaBuilder.addSchema(joinLines(
                "schema test {",
                "  document test { }",
                "  constant foo {",
                "    type: tensor(x{})",
                "    uri: http://somewhere.far.away:4080/in/another-galaxy",
                "  }",
                "}"
        ));
        schemaBuilder.build(true);
        Schema schema = schemaBuilder.getSchema();
        RankProfile.Constant constant = schema.constants().values().iterator().next();
        assertEquals(DistributableResource.PathType.URI, constant.pathType().get());
        assertEquals("http://somewhere.far.away:4080/in/another-galaxy", constant.valuePath().get());
    }

    @Test
    void constant_uri_no_dual_slashes_is_allowed() throws Exception {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder schemaBuilder = new ApplicationBuilder(rankProfileRegistry);
        schemaBuilder.addSchema(joinLines(
                "schema test {",
                "  document test { }",
                "  constant foo {",
                "    type: tensor(x{})",
                "    uri: http:somewhere.far.away/in/another-galaxy",
                "  }",
                "}"
        ));
        schemaBuilder.build(true);
        Schema schema = schemaBuilder.getSchema();
        RankProfile.Constant constant = schema.constants().values().iterator().next();
        assertEquals(DistributableResource.PathType.URI, constant.pathType().get());
        assertEquals("http:somewhere.far.away/in/another-galaxy", constant.valuePath().get());
    }

    @Test
    void constant_uri_only_supports_http_and_https() {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder schemaBuilder = new ApplicationBuilder(rankProfileRegistry);
        String expectedMessage = "Encountered \" <IDENTIFIER> \"ftp\"\" at line 5, column 10.\n\n" +
                "Was expecting:\n\n" +
                "<URI_PATH> ...";
        try {
            schemaBuilder.addSchema(joinLines(
                    "schema test {",
                    "  document test { }",
                    "  constant foo {",
                    "    type: tensor(x{})",
                    "    uri: ftp:somewhere.far.away/in/another-galaxy",
                    "  }",
                    "}"
            ));
        } catch (ParseException e) {
            if (!e.getMessage().startsWith(expectedMessage))
                fail("Expected exception with message starting with:\n'" + expectedMessage + "\nBut got:\n'" + e.getMessage());
        }
    }

}
