// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.parser;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.io.IOUtils;
import static com.yahoo.config.model.test.TestUtil.joinLines;

import java.io.File;

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

    ParsedSchema parseFile(String fileName) throws Exception {
        File file = new File(fileName);
        return parseString(IOUtils.readFile(file));
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

    void checkFileParses(String fileName) throws Exception {
        System.err.println("TRY parsing: "+fileName);
        var schema = parseFile(fileName);
        assertTrue(schema != null);
        assertTrue(schema.name() != null);
        assertTrue(! schema.name().equals(""));
    }

    @Test
    public void parse_various_old_sdfiles() throws Exception {
        checkFileParses("src/test/cfg/search/data/travel/schemas/TTData.sd");
        checkFileParses("src/test/cfg/search/data/travel/schemas/TTEdge.sd");
        checkFileParses("src/test/cfg/search/data/travel/schemas/TTPOI.sd");
        checkFileParses("src/test/configmodel/types/other_doc.sd");
        checkFileParses("src/test/configmodel/types/types.sd");
        checkFileParses("src/test/configmodel/types/type_with_doc_field.sd");
        checkFileParses("src/test/derived/array_of_struct_attribute/test.sd");
        checkFileParses("src/test/derived/deriver/child.sd");
        checkFileParses("src/test/derived/deriver/grandparent.sd");
        checkFileParses("src/test/derived/deriver/parent.sd");
        checkFileParses("src/test/derived/map_attribute/test.sd");
        checkFileParses("src/test/derived/map_of_struct_attribute/test.sd");
        checkFileParses("src/test/examples/arrays.sd");
        checkFileParses("src/test/examples/arraysweightedsets.sd");
        checkFileParses("src/test/examples/attributesettings.sd");
        checkFileParses("src/test/examples/attributesexactmatch.sd");
        checkFileParses("src/test/examples/badstruct.sd");
        checkFileParses("src/test/examples/casing.sd");
        checkFileParses("src/test/examples/comment.sd");
        checkFileParses("src/test/examples/documentidinsummary.sd");
        checkFileParses("src/test/examples/fieldoftypedocument.sd");
        checkFileParses("src/test/examples/implicitsummaries_attribute.sd");
        checkFileParses("src/test/examples/implicitsummaryfields.sd");
        checkFileParses("src/test/examples/incorrectrankingexpressionfileref.sd");
        checkFileParses("src/test/examples/indexing_extra.sd");
        checkFileParses("src/test/examples/indexing_modify_field_no_output.sd");
        checkFileParses("src/test/examples/indexing.sd");
        checkFileParses("src/test/examples/indexrewrite.sd");
        checkFileParses("src/test/examples/indexsettings.sd");
        checkFileParses("src/test/examples/integerindex2attribute.sd");
        checkFileParses("src/test/examples/invalidimplicitsummarysource.sd");
        checkFileParses("src/test/examples/multiplesummaries.sd");
        checkFileParses("src/test/examples/music.sd");
        checkFileParses("src/test/examples/nextgen/boldedsummaryfields.sd");
        checkFileParses("src/test/examples/nextgen/dynamicsummaryfields.sd");
        checkFileParses("src/test/examples/nextgen/extrafield.sd");
        checkFileParses("src/test/examples/nextgen/implicitstructtypes.sd");
        checkFileParses("src/test/examples/nextgen/simple.sd");
        checkFileParses("src/test/examples/nextgen/summaryfield.sd");
        checkFileParses("src/test/examples/nextgen/toggleon.sd");
        checkFileParses("src/test/examples/nextgen/untransformedsummaryfields.sd");
        checkFileParses("src/test/examples/ngram.sd");
        checkFileParses("src/test/examples/outsidedoc.sd");
        checkFileParses("src/test/examples/outsidesummary.sd");
        checkFileParses("src/test/examples/position_array.sd");
        checkFileParses("src/test/examples/position_attribute.sd");
        checkFileParses("src/test/examples/position_base.sd");
        checkFileParses("src/test/examples/position_extra.sd");
        checkFileParses("src/test/examples/position_index.sd");
        checkFileParses("src/test/examples/position_inherited.sd");
        checkFileParses("src/test/examples/position_summary.sd");
        checkFileParses("src/test/examples/rankmodifier/literal.sd");
        checkFileParses("src/test/examples/rankpropvars.sd");
        checkFileParses("src/test/examples/reserved_words_as_field_names.sd");
        checkFileParses("src/test/examples/simple.sd");
        checkFileParses("src/test/examples/stemmingdefault.sd");
        checkFileParses("src/test/examples/stemmingsetting.sd");
        checkFileParses("src/test/examples/strange.sd");
        checkFileParses("src/test/examples/structanddocumentwithsamenames.sd");
        checkFileParses("src/test/examples/struct.sd");
        checkFileParses("src/test/examples/summaryfieldcollision.sd");
        checkFileParses("src/test/examples/weightedset-summaryto.sd");
    }

}
