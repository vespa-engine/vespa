// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.io.IOUtils;
import static com.yahoo.config.model.test.TestUtil.joinLines;

import java.io.File;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author arnej
 */
public class SchemaParserTestCase {

    ParsedSchema parseString(String input) throws Exception {
        var deployLogger = new BaseDeployLogger();
        var modelProperties = new TestProperties();
        var stream = new SimpleCharStream(input);
        try {
            var parser = new SchemaParser(stream, deployLogger, modelProperties);
            return parser.schema();
        } catch (ParseException pe) {
            throw new ParseException(stream.formatException(pe.getMessage()));
        }
    }

    ParsedSchema parseFile(String fileName) throws Exception {
        File file = new File(fileName);
        return parseString(IOUtils.readFile(file));
    }

    @Test
    void minimal_schema_can_be_parsed() throws Exception {
        String input = joinLines
                ("schema foo {",
                        "  document foo {",
                        "  }",
                        "}");
        ParsedSchema schema = parseString(input);
        assertEquals("foo", schema.name());
        assertTrue(schema.hasDocument());
        assertEquals("foo", schema.getDocument().name());
    }

    @Test
    void document_only_can_be_parsed() throws Exception {
        String input = joinLines
                ("document bar {",
                        "}");
        ParsedSchema schema = parseString(input);
        assertEquals("bar", schema.name());
        assertTrue(schema.hasDocument());
        assertEquals("bar", schema.getDocument().name());
    }

    @Test
    void multiple_documents_disallowed() {
        String input = joinLines
                ("schema foo {",
                        "  document foo {",
                        "  }",
                        "  document foo2 {",
                        "  }",
                        "}");
        var e = assertThrows(IllegalArgumentException.class, () -> parseString(input));
        assertEquals("schema 'foo' error: already has document 'foo' so cannot add document 'foo2'", e.getMessage());
    }

    @Test
    void backwards_path_is_disallowed() {
        assertEquals("'..' is not allowed in path",
                assertThrows(IllegalArgumentException.class,
                        () -> parseString("schema foo {\n" +
                                "  constant my_constant_tensor {\n" +
                                "    file: foo/../bar\n" +
                                "    type: tensor<float>(x{},y{})\n" +
                                "  }\n" +
                                "}\n")).getMessage());
    }

    @Test
    void global_phase_can_be_parsed() throws Exception {
        String input = """
            schema foo {
                rank-profile normal {
                    first-phase {
                        expression {
                            rankingExpression(1.0)
                        }
                    }
                }
                rank-profile bar {
                    global-phase {
                        expression: onnx(mymodel)
                        rerank-count: 79
                    }
                }
            }
            """;
        ParsedSchema schema = parseString(input);
        assertEquals("foo", schema.name());
        var rplist = schema.getRankProfiles();
        assertEquals(2, rplist.size());
        var rp0 = rplist.get(0);
        assertEquals("normal", rp0.name());
        assertFalse(rp0.getGlobalPhaseRerankCount().isPresent());
        assertFalse(rp0.getGlobalPhaseExpression().isPresent());
        assertTrue(rp0.getFirstPhaseExpression().isPresent());
        assertEquals("rankingExpression(1.0)", rp0.getFirstPhaseExpression().get());
        var rp1 = rplist.get(1);
        assertEquals("bar", rp1.name());
        assertTrue(rp1.getGlobalPhaseRerankCount().isPresent());
        assertTrue(rp1.getGlobalPhaseExpression().isPresent());
        assertEquals(79, rp1.getGlobalPhaseRerankCount().get());
        assertEquals("onnx(mymodel)", rp1.getGlobalPhaseExpression().get());
    }

    void checkFileParses(String fileName) throws Exception {
        var schema = parseFile(fileName);
        assertNotNull(schema);
        assertNotNull(schema.name());
        assertNotEquals("", schema.name());
    }

    // TODO: Many (all)? of the files below are parsed from other tests and can be removed from here
    @Test
    void parse_various_old_sdfiles() throws Exception {
        checkFileParses("src/test/cfg/search/data/travel/schemas/TTData.sd");
        checkFileParses("src/test/cfg/search/data/travel/schemas/TTEdge.sd");
        checkFileParses("src/test/cfg/search/data/travel/schemas/TTPOI.sd");
        checkFileParses("src/test/configmodel/types/other_doc.sd");
        checkFileParses("src/test/configmodel/types/types.sd");
        checkFileParses("src/test/configmodel/types/type_with_doc_field.sd");
        checkFileParses("src/test/derived/advanced/advanced.sd");
        checkFileParses("src/test/derived/annotationsimplicitstruct/annotationsimplicitstruct.sd");
        checkFileParses("src/test/derived/annotationsinheritance2/annotationsinheritance2.sd");
        checkFileParses("src/test/derived/annotationsinheritance/annotationsinheritance.sd");
        checkFileParses("src/test/derived/annotationsoutsideofdocument/annotationsoutsideofdocument.sd");
        checkFileParses("src/test/derived/annotationspolymorphy/annotationspolymorphy.sd");
        checkFileParses("src/test/derived/annotationsreference2/annotationsreference2.sd");
        checkFileParses("src/test/derived/annotationsreference/annotationsreference.sd");
        checkFileParses("src/test/derived/annotationssimple/annotationssimple.sd");
        checkFileParses("src/test/derived/annotationsstruct/annotationsstruct.sd");
        checkFileParses("src/test/derived/annotationsstructarray/annotationsstructarray.sd");
        checkFileParses("src/test/derived/array_of_struct_attribute/test.sd");
        checkFileParses("src/test/derived/arrays/arrays.sd");
        checkFileParses("src/test/derived/attributeprefetch/attributeprefetch.sd");
        checkFileParses("src/test/derived/attributerank/attributerank.sd");
        checkFileParses("src/test/derived/attributes/attributes.sd");
        checkFileParses("src/test/derived/combinedattributeandindexsearch/combinedattributeandindexsearch.sd");
        checkFileParses("src/test/derived/complex/complex.sd");
        checkFileParses("src/test/derived/deriver/child.sd");
        checkFileParses("src/test/derived/deriver/grandparent.sd");
        checkFileParses("src/test/derived/deriver/parent.sd");
        checkFileParses("src/test/derived/emptychild/child.sd");
        checkFileParses("src/test/derived/emptychild/parent.sd");
        checkFileParses("src/test/derived/emptydefault/emptydefault.sd");
        checkFileParses("src/test/derived/exactmatch/exactmatch.sd");
        checkFileParses("src/test/derived/fieldset/test.sd");
        checkFileParses("src/test/derived/flickr/flickrphotos.sd");
        checkFileParses("src/test/derived/function_arguments/test.sd");
        checkFileParses("src/test/derived/function_arguments_with_expressions/test.sd");
        checkFileParses("src/test/derived/gemini2/gemini.sd");
        checkFileParses("src/test/derived/hnsw_index/test.sd");
        checkFileParses("src/test/derived/id/id.sd");
        checkFileParses("src/test/derived/importedfields/child.sd");
        checkFileParses("src/test/derived/importedfields/grandparent.sd");
        checkFileParses("src/test/derived/imported_fields_inherited_reference/child_a.sd");
        checkFileParses("src/test/derived/imported_fields_inherited_reference/child_b.sd");
        checkFileParses("src/test/derived/imported_fields_inherited_reference/child_c.sd");
        checkFileParses("src/test/derived/imported_fields_inherited_reference/parent.sd");
        checkFileParses("src/test/derived/importedfields/parent_a.sd");
        checkFileParses("src/test/derived/importedfields/parent_b.sd");
        checkFileParses("src/test/derived/imported_position_field/child.sd");
        checkFileParses("src/test/derived/imported_position_field/parent.sd");
        checkFileParses("src/test/derived/imported_position_field_summary/child.sd");
        checkFileParses("src/test/derived/imported_position_field_summary/parent.sd");
        checkFileParses("src/test/derived/imported_struct_fields/child.sd");
        checkFileParses("src/test/derived/imported_struct_fields/parent.sd");
        checkFileParses("src/test/derived/indexinfo_fieldsets/indexinfo_fieldsets.sd");
        checkFileParses("src/test/derived/indexinfo_lowercase/indexinfo_lowercase.sd");
        checkFileParses("src/test/derived/indexschema/indexschema.sd");
        checkFileParses("src/test/derived/indexswitches/indexswitches.sd");
        checkFileParses("src/test/derived/inheritance/child.sd");
        checkFileParses("src/test/derived/inheritance/father.sd");
        checkFileParses("src/test/derived/inheritance/grandparent.sd");
        checkFileParses("src/test/derived/inheritance/mother.sd");
        checkFileParses("src/test/derived/inheritdiamond/child.sd");
        checkFileParses("src/test/derived/inheritdiamond/father.sd");
        checkFileParses("src/test/derived/inheritdiamond/grandparent.sd");
        checkFileParses("src/test/derived/inheritdiamond/mother.sd");
        checkFileParses("src/test/derived/inheritfromgrandparent/child.sd");
        checkFileParses("src/test/derived/inheritfromgrandparent/grandparent.sd");
        checkFileParses("src/test/derived/inheritfromgrandparent/parent.sd");
        checkFileParses("src/test/derived/inheritfromnull/inheritfromnull.sd");
        checkFileParses("src/test/derived/inheritfromparent/child.sd");
        checkFileParses("src/test/derived/inheritfromparent/parent.sd");
        checkFileParses("src/test/derived/inheritstruct/child.sd");
        checkFileParses("src/test/derived/inheritstruct/parent.sd");
        checkFileParses("src/test/derived/integerattributetostringindex/integerattributetostringindex.sd");
        checkFileParses("src/test/derived/language/language.sd");
        checkFileParses("src/test/derived/lowercase/lowercase.sd");
        checkFileParses("src/test/derived/mail/mail.sd");
        checkFileParses("src/test/derived/map_attribute/test.sd");
        checkFileParses("src/test/derived/map_of_struct_attribute/test.sd");
        checkFileParses("src/test/derived/mlr/mlr.sd");
        checkFileParses("src/test/derived/music3/music3.sd");
        checkFileParses("src/test/derived/music/music.sd");
        checkFileParses("src/test/derived/namecollision/collision.sd");
        checkFileParses("src/test/derived/namecollision/collisionstruct.sd");
        checkFileParses("src/test/derived/nearestneighbor/test.sd");
        checkFileParses("src/test/derived/newrank/newrank.sd");
        checkFileParses("src/test/derived/nuwa/newsindex.sd");
        checkFileParses("src/test/derived/orderilscripts/orderilscripts.sd");
        checkFileParses("src/test/derived/position_array/position_array.sd");
        checkFileParses("src/test/derived/position_attribute/position_attribute.sd");
        checkFileParses("src/test/derived/position_extra/position_extra.sd");
        checkFileParses("src/test/derived/position_nosummary/position_nosummary.sd");
        checkFileParses("src/test/derived/position_summary/position_summary.sd");
        checkFileParses("src/test/derived/predicate_attribute/predicate_attribute.sd");
        checkFileParses("src/test/derived/prefixexactattribute/prefixexactattribute.sd");
        checkFileParses("src/test/derived/rankingexpression/rankexpression.sd");
        checkFileParses("src/test/derived/rankprofileinheritance/child.sd");
        checkFileParses("src/test/derived/rankprofileinheritance/parent1.sd");
        checkFileParses("src/test/derived/rankprofileinheritance/parent2.sd");
        checkFileParses("src/test/derived/rankprofilemodularity/test.sd");
        checkFileParses("src/test/derived/rankprofiles/rankprofiles.sd");
        checkFileParses("src/test/derived/rankproperties/rankproperties.sd");
        checkFileParses("src/test/derived/ranktypes/ranktypes.sd");
        checkFileParses("src/test/derived/reference_fields/ad.sd");
        checkFileParses("src/test/derived/reference_fields/campaign.sd");
        checkFileParses("src/test/derived/renamedfeatures/foo.sd");
        checkFileParses("src/test/derived/reserved_position/reserved_position.sd");
        checkFileParses("src/test/derived/schemainheritance/child.sd");
        checkFileParses("src/test/derived/schemainheritance/importedschema.sd");
        checkFileParses("src/test/derived/schemainheritance/parent.sd");
        checkFileParses("src/test/derived/slice/test.sd");
        checkFileParses("src/test/derived/streamingjuniper/streamingjuniper.sd");
        checkFileParses("src/test/derived/streamingstructdefault/streamingstructdefault.sd");
        checkFileParses("src/test/derived/streamingstruct/streamingstruct.sd");
        checkFileParses("src/test/derived/structandfieldset/test.sd");
        checkFileParses("src/test/derived/structanyorder/structanyorder.sd");
        checkFileParses("src/test/derived/structinheritance/bad.sd");
        checkFileParses("src/test/derived/structinheritance/simple.sd");
        checkFileParses("src/test/derived/tensor2/first.sd");
        checkFileParses("src/test/derived/tensor2/second.sd");
        checkFileParses("src/test/derived/tensor/tensor.sd");
        checkFileParses("src/test/derived/tokenization/tokenization.sd");
        checkFileParses("src/test/derived/twostreamingstructs/streamingstruct.sd");
        checkFileParses("src/test/derived/twostreamingstructs/whatever.sd");
        checkFileParses("src/test/derived/types/types.sd");
        checkFileParses("src/test/derived/uri_array/uri_array.sd");
        checkFileParses("src/test/derived/uri_wset/uri_wset.sd");
        checkFileParses("src/test/examples/arrays.sd");
        checkFileParses("src/test/examples/arraysweightedsets.sd");
        checkFileParses("src/test/examples/attributeposition.sd");
        checkFileParses("src/test/examples/attributesettings.sd");
        checkFileParses("src/test/examples/attributesexactmatch.sd");
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
        checkFileParses("src/test/examples/struct.sd");
        checkFileParses("src/test/examples/summaryfieldcollision.sd");
        checkFileParses("src/test/examples/weightedset-summaryto.sd");
    }
}
