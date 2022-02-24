// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.parser;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.deploy.TestProperties;
import static com.yahoo.config.model.test.TestUtil.joinLines;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

/**
 * @author arnej
 */
public class IntermediateParserTestCase {

    ParsedSchema parseString(String input) throws Exception {
        var deployLogger = new BaseDeployLogger();
        var modelProperties = new TestProperties();
        var stream = new SimpleCharStream(input);
        var parser = new IntermediateParser(stream, deployLogger, modelProperties);
        return parser.schema();
    }

    @Test
    public void minimal_schema_can_be_parsed() throws Exception {
        String input = joinLines
            ("schema foo {",
             "  document bar {",
             "  }",
             "}");
        ParsedSchema schema = parseString(input);
        assertEquals("foo", schema.name());
        assertTrue(schema.hasDocument());
        assertEquals("bar", schema.getDocument().name());
    }

    @Test
    public void document_only_can_be_parsed() throws Exception {
        String input = joinLines
            ("document bar {",
             "}");
        ParsedSchema schema = parseString(input);
        assertEquals("bar", schema.name());
        assertTrue(schema.hasDocument());
        assertEquals("bar", schema.getDocument().name());
    }

    @Test
    public void multiple_documents_disallowed() throws Exception {
        String input = joinLines
            ("schema foo {",
             "  document foo1 {",
             "  }",
             "  document foo2 {",
             "  }",
             "}");
        var e = assertThrows(IllegalArgumentException.class, () -> parseString(input));
        assertEquals("schema 'foo' error: already has document foo1 so cannot add document foo2", e.getMessage());
    }
}
