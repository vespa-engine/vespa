package ai.vespa.schemals.parser;

import com.yahoo.schema.parser.ParsedSchema;
import static com.yahoo.config.model.test.TestUtil.joinLines;
import com.yahoo.io.IOUtils;

import java.io.File;
import java.io.PrintStream;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SchemaParserTest {
    ParsedSchema parseString(String input, String fileName) throws Exception {
        CharSequence sequence = input;
        PrintStream logger = System.out;

        SchemaParser parserStrict = new SchemaParser(logger, fileName, sequence);
        parserStrict.setParserTolerant(false);

        ParsedSchema root = parserStrict.Root();
        return root;
    }

    ParsedSchema parseString(String input) throws Exception {
        return parseString(input, "<FROMSTRING>");
    }

    ParsedSchema parseFile(String fileName) throws Exception {
        File file = new File(fileName);
        return parseString(IOUtils.readFile(file), fileName);
    }

    void checkFileParses(String fileName) throws Exception {
        try {
            var schema = parseFile(fileName);
            assertNotNull(schema);
            assertNotNull(schema.name());
            assertNotEquals("", schema.name());
        } catch(ParseException pe) {
            throw new ParseException("For file " + fileName + ": " + pe.getMessage());
        } catch(IllegalArgumentException iae) {
            throw new IllegalArgumentException("For file " + fileName + ": " + iae.getMessage());
        }
    }

    @Test
    void minimalSchemaParsed() throws Exception {
        String input = joinLines
            ("schema foo {",
             "  document foo {",
             "  }",
             "}");
        ParsedSchema schema = parseString(input);
        assertEquals(schema.name(), "foo");
    }

    @Test
    void noDuplicateDocuments() throws Exception {
        String input = joinLines
            ("schema foo {",
             "  document foo {",
             "  }",
             "  document bar {",
             "  }",
             "}");
        assertThrows(IllegalArgumentException.class, () -> parseString(input), "Expected parser to throw on duplicate document in Schema!");
    }

    @Test
    void parsingFatTest() throws Exception {
        checkFileParses("../../../config-model/src/test/cfg/search/data/travel/schemas/TTData.sd");
        checkFileParses("../../../config-model/src/test/cfg/search/data/travel/schemas/TTEdge.sd");
        checkFileParses("../../../config-model/src/test/cfg/search/data/travel/schemas/TTPOI.sd");
        checkFileParses("../../../config-model/src/test/configmodel/types/other_doc.sd");
        checkFileParses("../../../config-model/src/test/configmodel/types/types.sd");
        checkFileParses("../../../config-model/src/test/configmodel/types/type_with_doc_field.sd");
        checkFileParses("../../../config-model/src/test/derived/advanced/advanced.sd");
        checkFileParses("../../../config-model/src/test/derived/annotationsimplicitstruct/annotationsimplicitstruct.sd");
        checkFileParses("../../../config-model/src/test/derived/annotationsinheritance2/annotationsinheritance2.sd");
        checkFileParses("../../../config-model/src/test/derived/annotationsinheritance/annotationsinheritance.sd");
        checkFileParses("../../../config-model/src/test/derived/annotationsoutsideofdocument/annotationsoutsideofdocument.sd");
        checkFileParses("../../../config-model/src/test/derived/annotationspolymorphy/annotationspolymorphy.sd");
        checkFileParses("../../../config-model/src/test/derived/annotationsreference2/annotationsreference2.sd");
        checkFileParses("../../../config-model/src/test/derived/annotationsreference/annotationsreference.sd");
        checkFileParses("../../../config-model/src/test/derived/annotationssimple/annotationssimple.sd");
        checkFileParses("../../../config-model/src/test/derived/annotationsstruct/annotationsstruct.sd");
        checkFileParses("../../../config-model/src/test/derived/annotationsstructarray/annotationsstructarray.sd");
        checkFileParses("../../../config-model/src/test/derived/array_of_struct_attribute/test.sd");
        checkFileParses("../../../config-model/src/test/derived/arrays/arrays.sd");
        checkFileParses("../../../config-model/src/test/derived/attributeprefetch/attributeprefetch.sd");
        checkFileParses("../../../config-model/src/test/derived/attributerank/attributerank.sd");
        checkFileParses("../../../config-model/src/test/derived/attributes/attributes.sd");
        checkFileParses("../../../config-model/src/test/derived/combinedattributeandindexsearch/combinedattributeandindexsearch.sd");
        checkFileParses("../../../config-model/src/test/derived/complex/complex.sd");
        checkFileParses("../../../config-model/src/test/derived/deriver/child.sd");
        checkFileParses("../../../config-model/src/test/derived/deriver/grandparent.sd");
        checkFileParses("../../../config-model/src/test/derived/deriver/parent.sd");
        checkFileParses("../../../config-model/src/test/derived/emptychild/child.sd");
        checkFileParses("../../../config-model/src/test/derived/emptychild/parent.sd");
        checkFileParses("../../../config-model/src/test/derived/emptydefault/emptydefault.sd");
        checkFileParses("../../../config-model/src/test/derived/exactmatch/exactmatch.sd");
        checkFileParses("../../../config-model/src/test/derived/fieldset/test.sd");
        checkFileParses("../../../config-model/src/test/derived/flickr/flickrphotos.sd");
        checkFileParses("../../../config-model/src/test/derived/function_arguments/test.sd");
        checkFileParses("../../../config-model/src/test/derived/function_arguments_with_expressions/test.sd");
        checkFileParses("../../../config-model/src/test/derived/gemini2/gemini.sd");
        checkFileParses("../../../config-model/src/test/derived/hnsw_index/test.sd");
        checkFileParses("../../../config-model/src/test/derived/id/id.sd");
        checkFileParses("../../../config-model/src/test/derived/importedfields/child.sd");
        checkFileParses("../../../config-model/src/test/derived/importedfields/grandparent.sd");
        checkFileParses("../../../config-model/src/test/derived/imported_fields_inherited_reference/child_a.sd");
        checkFileParses("../../../config-model/src/test/derived/imported_fields_inherited_reference/child_b.sd");
        checkFileParses("../../../config-model/src/test/derived/imported_fields_inherited_reference/child_c.sd");
        checkFileParses("../../../config-model/src/test/derived/imported_fields_inherited_reference/parent.sd");
        checkFileParses("../../../config-model/src/test/derived/importedfields/parent_a.sd");
        checkFileParses("../../../config-model/src/test/derived/importedfields/parent_b.sd");
        checkFileParses("../../../config-model/src/test/derived/imported_position_field/child.sd");
        checkFileParses("../../../config-model/src/test/derived/imported_position_field/parent.sd");
        checkFileParses("../../../config-model/src/test/derived/imported_position_field_summary/child.sd");
        checkFileParses("../../../config-model/src/test/derived/imported_position_field_summary/parent.sd");
        checkFileParses("../../../config-model/src/test/derived/imported_struct_fields/child.sd");
        checkFileParses("../../../config-model/src/test/derived/imported_struct_fields/parent.sd");
        checkFileParses("../../../config-model/src/test/derived/indexinfo_fieldsets/indexinfo_fieldsets.sd");
        checkFileParses("../../../config-model/src/test/derived/indexinfo_lowercase/indexinfo_lowercase.sd");
        checkFileParses("../../../config-model/src/test/derived/indexschema/indexschema.sd");
        checkFileParses("../../../config-model/src/test/derived/indexswitches/indexswitches.sd");
        checkFileParses("../../../config-model/src/test/derived/inheritance/child.sd");
        checkFileParses("../../../config-model/src/test/derived/inheritance/father.sd");
        checkFileParses("../../../config-model/src/test/derived/inheritance/grandparent.sd");
        checkFileParses("../../../config-model/src/test/derived/inheritance/mother.sd");
        checkFileParses("../../../config-model/src/test/derived/inheritdiamond/child.sd");
        checkFileParses("../../../config-model/src/test/derived/inheritdiamond/father.sd");
        checkFileParses("../../../config-model/src/test/derived/inheritdiamond/grandparent.sd");
        checkFileParses("../../../config-model/src/test/derived/inheritdiamond/mother.sd");
        checkFileParses("../../../config-model/src/test/derived/inheritfromgrandparent/child.sd");
        checkFileParses("../../../config-model/src/test/derived/inheritfromgrandparent/grandparent.sd");
        checkFileParses("../../../config-model/src/test/derived/inheritfromgrandparent/parent.sd");
        checkFileParses("../../../config-model/src/test/derived/inheritfromnull/inheritfromnull.sd");
        checkFileParses("../../../config-model/src/test/derived/inheritfromparent/child.sd");
        checkFileParses("../../../config-model/src/test/derived/inheritfromparent/parent.sd");
        checkFileParses("../../../config-model/src/test/derived/inheritstruct/child.sd");
        checkFileParses("../../../config-model/src/test/derived/inheritstruct/parent.sd");
        checkFileParses("../../../config-model/src/test/derived/integerattributetostringindex/integerattributetostringindex.sd");
        checkFileParses("../../../config-model/src/test/derived/language/language.sd");
        checkFileParses("../../../config-model/src/test/derived/lowercase/lowercase.sd");
        checkFileParses("../../../config-model/src/test/derived/mail/mail.sd");
        checkFileParses("../../../config-model/src/test/derived/map_attribute/test.sd");
        checkFileParses("../../../config-model/src/test/derived/map_of_struct_attribute/test.sd");
        checkFileParses("../../../config-model/src/test/derived/mlr/mlr.sd");
        checkFileParses("../../../config-model/src/test/derived/music3/music3.sd");
        checkFileParses("../../../config-model/src/test/derived/music/music.sd");
        checkFileParses("../../../config-model/src/test/derived/namecollision/collision.sd");
        checkFileParses("../../../config-model/src/test/derived/namecollision/collisionstruct.sd");
        checkFileParses("../../../config-model/src/test/derived/nearestneighbor/test.sd");
        checkFileParses("../../../config-model/src/test/derived/newrank/newrank.sd");
        checkFileParses("../../../config-model/src/test/derived/nuwa/newsindex.sd");
        checkFileParses("../../../config-model/src/test/derived/orderilscripts/orderilscripts.sd");
        checkFileParses("../../../config-model/src/test/derived/position_array/position_array.sd");
        checkFileParses("../../../config-model/src/test/derived/position_attribute/position_attribute.sd");
        checkFileParses("../../../config-model/src/test/derived/position_extra/position_extra.sd");
        checkFileParses("../../../config-model/src/test/derived/position_nosummary/position_nosummary.sd");
        checkFileParses("../../../config-model/src/test/derived/position_summary/position_summary.sd");
        checkFileParses("../../../config-model/src/test/derived/predicate_attribute/predicate_attribute.sd");
        checkFileParses("../../../config-model/src/test/derived/prefixexactattribute/prefixexactattribute.sd");
        checkFileParses("../../../config-model/src/test/derived/rankingexpression/rankexpression.sd");
        checkFileParses("../../../config-model/src/test/derived/rankprofileinheritance/child.sd");
        checkFileParses("../../../config-model/src/test/derived/rankprofileinheritance/parent1.sd");
        checkFileParses("../../../config-model/src/test/derived/rankprofileinheritance/parent2.sd");
        checkFileParses("../../../config-model/src/test/derived/rankprofilemodularity/test.sd");
        checkFileParses("../../../config-model/src/test/derived/rankprofiles/rankprofiles.sd");
        checkFileParses("../../../config-model/src/test/derived/rankproperties/rankproperties.sd");
        checkFileParses("../../../config-model/src/test/derived/ranktypes/ranktypes.sd");
        checkFileParses("../../../config-model/src/test/derived/reference_fields/ad.sd");
        checkFileParses("../../../config-model/src/test/derived/reference_fields/campaign.sd");
        checkFileParses("../../../config-model/src/test/derived/renamedfeatures/foo.sd");
        checkFileParses("../../../config-model/src/test/derived/reserved_position/reserved_position.sd");
        checkFileParses("../../../config-model/src/test/derived/schemainheritance/child.sd");
        checkFileParses("../../../config-model/src/test/derived/schemainheritance/importedschema.sd");
        checkFileParses("../../../config-model/src/test/derived/schemainheritance/parent.sd");
        checkFileParses("../../../config-model/src/test/derived/slice/test.sd");
        checkFileParses("../../../config-model/src/test/derived/streamingjuniper/streamingjuniper.sd");
        checkFileParses("../../../config-model/src/test/derived/streamingstructdefault/streamingstructdefault.sd");
        checkFileParses("../../../config-model/src/test/derived/streamingstruct/streamingstruct.sd");
        checkFileParses("../../../config-model/src/test/derived/structandfieldset/test.sd");
        checkFileParses("../../../config-model/src/test/derived/structanyorder/structanyorder.sd");
        checkFileParses("../../../config-model/src/test/derived/structinheritance/bad.sd");
        checkFileParses("../../../config-model/src/test/derived/structinheritance/simple.sd");
        checkFileParses("../../../config-model/src/test/derived/tensor2/first.sd");
        checkFileParses("../../../config-model/src/test/derived/tensor2/second.sd");
        checkFileParses("../../../config-model/src/test/derived/tensor/tensor.sd");
        checkFileParses("../../../config-model/src/test/derived/tokenization/tokenization.sd");
        checkFileParses("../../../config-model/src/test/derived/twostreamingstructs/streamingstruct.sd");
        checkFileParses("../../../config-model/src/test/derived/twostreamingstructs/whatever.sd");
        checkFileParses("../../../config-model/src/test/derived/types/types.sd");
        checkFileParses("../../../config-model/src/test/derived/uri_array/uri_array.sd");
        checkFileParses("../../../config-model/src/test/derived/uri_wset/uri_wset.sd");
        checkFileParses("../../../config-model/src/test/examples/arrays.sd");
        checkFileParses("../../../config-model/src/test/examples/arraysweightedsets.sd");
        checkFileParses("../../../config-model/src/test/examples/attributeposition.sd");
        checkFileParses("../../../config-model/src/test/examples/attributesettings.sd");
        checkFileParses("../../../config-model/src/test/examples/attributesexactmatch.sd");
        checkFileParses("../../../config-model/src/test/examples/casing.sd");
        checkFileParses("../../../config-model/src/test/examples/comment.sd");
        checkFileParses("../../../config-model/src/test/examples/documentidinsummary.sd");
        checkFileParses("../../../config-model/src/test/examples/fieldoftypedocument.sd");
        checkFileParses("../../../config-model/src/test/examples/implicitsummaries_attribute.sd");
        checkFileParses("../../../config-model/src/test/examples/implicitsummaryfields.sd");
        checkFileParses("../../../config-model/src/test/examples/incorrectrankingexpressionfileref.sd");
        checkFileParses("../../../config-model/src/test/examples/indexing_extra.sd");
        checkFileParses("../../../config-model/src/test/examples/indexing.sd");
        checkFileParses("../../../config-model/src/test/examples/indexrewrite.sd");
        checkFileParses("../../../config-model/src/test/examples/indexsettings.sd");
        checkFileParses("../../../config-model/src/test/examples/integerindex2attribute.sd");
        checkFileParses("../../../config-model/src/test/examples/invalidimplicitsummarysource.sd");
        checkFileParses("../../../config-model/src/test/examples/multiplesummaries.sd");
        checkFileParses("../../../config-model/src/test/examples/music.sd");
        checkFileParses("../../../config-model/src/test/examples/nextgen/boldedsummaryfields.sd");
        checkFileParses("../../../config-model/src/test/examples/nextgen/dynamicsummaryfields.sd");
        checkFileParses("../../../config-model/src/test/examples/nextgen/extrafield.sd");
        checkFileParses("../../../config-model/src/test/examples/nextgen/implicitstructtypes.sd");
        checkFileParses("../../../config-model/src/test/examples/nextgen/simple.sd");
        checkFileParses("../../../config-model/src/test/examples/nextgen/summaryfield.sd");
        checkFileParses("../../../config-model/src/test/examples/nextgen/toggleon.sd");
        checkFileParses("../../../config-model/src/test/examples/nextgen/untransformedsummaryfields.sd");
        checkFileParses("../../../config-model/src/test/examples/ngram.sd");
        checkFileParses("../../../config-model/src/test/examples/outsidedoc.sd");
        checkFileParses("../../../config-model/src/test/examples/outsidesummary.sd");
        checkFileParses("../../../config-model/src/test/examples/position_array.sd");
        checkFileParses("../../../config-model/src/test/examples/position_attribute.sd");
        checkFileParses("../../../config-model/src/test/examples/position_base.sd");
        checkFileParses("../../../config-model/src/test/examples/position_extra.sd");
        checkFileParses("../../../config-model/src/test/examples/position_index.sd");
        checkFileParses("../../../config-model/src/test/examples/position_inherited.sd");
        checkFileParses("../../../config-model/src/test/examples/position_summary.sd");
        checkFileParses("../../../config-model/src/test/examples/rankmodifier/literal.sd");
        checkFileParses("../../../config-model/src/test/examples/rankpropvars.sd");
        checkFileParses("../../../config-model/src/test/examples/reserved_words_as_field_names.sd");
        checkFileParses("../../../config-model/src/test/examples/simple.sd");
        checkFileParses("../../../config-model/src/test/examples/stemmingdefault.sd");
        checkFileParses("../../../config-model/src/test/examples/stemmingsetting.sd");
        checkFileParses("../../../config-model/src/test/examples/strange.sd");
        checkFileParses("../../../config-model/src/test/examples/struct.sd");
        checkFileParses("../../../config-model/src/test/examples/summaryfieldcollision.sd");
        checkFileParses("../../../config-model/src/test/examples/weightedset-summaryto.sd");
    }
}
