package ai.vespa.schemals;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import com.yahoo.io.IOUtils;

import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.common.FileUtils;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.schemadocument.SchemaDocument;
import ai.vespa.schemals.schemadocument.SchemaDocument.ParseResult;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;

import ai.vespa.schemals.testutils.*;

public class SchemaParserTest {
    static long countErrors(List<Diagnostic> diagnostics) {
        return diagnostics.stream()
                          .filter(diag -> diag.getSeverity() == DiagnosticSeverity.Error || diag.getSeverity() == null)
                          .count();
    }

    static String constructDiagnosticMessage(List<Diagnostic> diagnostics, int indent) {
        String message = "";
        for (var diagnostic : diagnostics) {
            String severityString = "";
            if (diagnostic.getSeverity() == null) severityString = "No Severity";
            else severityString = diagnostic.getSeverity().toString();

            Position start = diagnostic.getRange().getStart();
            message += "\n" + new String(new char[indent]).replace('\0', '\t') +
                "Diagnostic" + "[" + severityString + "]: \"" + diagnostic.getMessage() + "\", at position: (" + start.getLine() + ", " + start.getCharacter() + ")";

        }
        return message;
    }

    ParseResult parseString(String input, String fileName) throws Exception {
        SchemaMessageHandler messageHandler = new TestSchemaMessageHandler();
        ClientLogger logger = new TestLogger(messageHandler);
        SchemaIndex schemaIndex = new SchemaIndex(logger);
        TestSchemaDiagnosticsHandler diagnosticsHandler = new TestSchemaDiagnosticsHandler(new ArrayList<>());
        SchemaDocumentScheduler scheduler = new SchemaDocumentScheduler(logger, diagnosticsHandler, schemaIndex, messageHandler);
        schemaIndex.clearDocument(fileName);
        ParseContext context = new ParseContext(input, logger, fileName, schemaIndex, scheduler);
        context.useDocumentIdentifiers();
        return SchemaDocument.parseContent(context);
    }

    ParseResult parseString(String input) throws Exception {
        return parseString(input, "<FROMSTRING>");
    }

    ParseResult parseFile(String fileName) throws Exception {
        File file = new File(fileName);
        return parseString(IOUtils.readFile(file), fileName);
    }

    void checkFileParses(String fileName) throws Exception {
        try {
            var parseResult = parseFile(fileName);
            String testMessage = "For file: " + fileName + constructDiagnosticMessage(parseResult.diagnostics(), 1);
            assertEquals(0, countErrors(parseResult.diagnostics()), testMessage);
        } catch(Exception e) {
            throw new Exception(fileName + "\n" + e.getMessage());
        }
    }

    void checkFileFails(String fileName, int expectedErrors) throws Exception {
        var parseResult = parseFile(fileName);
        String testMessage = "For file: " + fileName + constructDiagnosticMessage(parseResult.diagnostics(), 1);
        assertEquals(expectedErrors, countErrors(parseResult.diagnostics()), testMessage);
    }

    void checkDirectoryParses(String directoryPath) throws Exception {
        String directoryURI = new File(directoryPath).toURI().toString();

        SchemaMessageHandler messageHandler = new TestSchemaMessageHandler();
        ClientLogger logger = new TestLogger(messageHandler);
        SchemaIndex schemaIndex = new SchemaIndex(logger);

        List<Diagnostic> diagnostics = new ArrayList<>();
        SchemaDiagnosticsHandler diagnosticsHandler = new TestSchemaDiagnosticsHandler(diagnostics);
        SchemaDocumentScheduler scheduler = new SchemaDocumentScheduler(logger, diagnosticsHandler, schemaIndex, messageHandler);

        scheduler.setupWorkspace(URI.create(directoryURI));

        List<String> schemaFiles = FileUtils.findSchemaFiles(directoryURI, logger);
        List<String> rankProfileFiles = FileUtils.findRankProfileFiles(directoryURI, logger);

        diagnostics.clear();

        String testMessage = "\nFor directory: " + directoryPath;
        int numErrors = 0;
        for (String rankProfileURI : rankProfileFiles) {
            diagnostics.clear();
            var documentHandle = scheduler.getRankProfileDocument(rankProfileURI);
            documentHandle.reparseContent();
            testMessage += "\n    File: " + rankProfileURI + constructDiagnosticMessage(diagnostics, 2);

            numErrors += countErrors(diagnostics);
        }

        for (String schemaURI : schemaFiles) {
            diagnostics.clear();
            var documentHandle = scheduler.getSchemaDocument(schemaURI);
            documentHandle.reparseContent();
            testMessage += "\n    File: " + schemaURI + constructDiagnosticMessage(diagnostics, 2);

            numErrors += countErrors(diagnostics);
        }

        assertEquals(0, numErrors, testMessage);
    }

    @TestFactory
    Stream<DynamicTest> generateGoodFileTests() {
        String[] filePaths = new String[] {
            "../../../config-model/src/test/derived/advanced/advanced.sd",
            "../../../config-model/src/test/derived/annotationsimplicitstruct/annotationsimplicitstruct.sd",
            "../../../config-model/src/test/derived/annotationsinheritance/annotationsinheritance.sd",
            "../../../config-model/src/test/derived/annotationsinheritance2/annotationsinheritance2.sd",
            "../../../config-model/src/test/derived/annotationsoutsideofdocument/annotationsoutsideofdocument.sd",
            "../../../config-model/src/test/derived/annotationspolymorphy/annotationspolymorphy.sd",
            "../../../config-model/src/test/derived/annotationsreference/annotationsreference.sd",
            "../../../config-model/src/test/derived/annotationsreference2/annotationsreference2.sd",
            "../../../config-model/src/test/derived/annotationssimple/annotationssimple.sd",
            "../../../config-model/src/test/derived/annotationsstruct/annotationsstruct.sd",
            "../../../config-model/src/test/derived/annotationsstructarray/annotationsstructarray.sd",
            "../../../config-model/src/test/derived/array_of_struct_attribute/test.sd",
            "../../../config-model/src/test/derived/arrays/arrays.sd",
            "../../../config-model/src/test/derived/attributeprefetch/attributeprefetch.sd",
            "../../../config-model/src/test/derived/attributerank/attributerank.sd",
            "../../../config-model/src/test/derived/attributes/attributes.sd",
            "../../../config-model/src/test/derived/combinedattributeandindexsearch/combinedattributeandindexsearch.sd",
            "../../../config-model/src/test/derived/complex/complex.sd",
            "../../../config-model/src/test/derived/emptydefault/emptydefault.sd",
            "../../../config-model/src/test/derived/exactmatch/exactmatch.sd",
            "../../../config-model/src/test/derived/fieldset/test.sd",
            "../../../config-model/src/test/derived/function_arguments_with_expressions/test.sd",
            "../../../config-model/src/test/derived/gemini2/gemini.sd",
            "../../../config-model/src/test/derived/hnsw_index/test.sd",
            "../../../config-model/src/test/derived/id/id.sd",
            "../../../config-model/src/test/derived/indexinfo_fieldsets/indexinfo_fieldsets.sd",
            "../../../config-model/src/test/derived/indexinfo_lowercase/indexinfo_lowercase.sd",
            "../../../config-model/src/test/derived/indexschema/indexschema.sd",
            "../../../config-model/src/test/derived/indexswitches/indexswitches.sd",
            "../../../config-model/src/test/derived/integerattributetostringindex/integerattributetostringindex.sd",
            "../../../config-model/src/test/derived/language/language.sd",
            "../../../config-model/src/test/derived/lowercase/lowercase.sd",
            "../../../config-model/src/test/derived/mail/mail.sd",
            "../../../config-model/src/test/derived/map_attribute/test.sd",
            "../../../config-model/src/test/derived/map_of_struct_attribute/test.sd",
            "../../../config-model/src/test/derived/mlr/mlr.sd",
            "../../../config-model/src/test/derived/music/music.sd",
            "../../../config-model/src/test/derived/music3/music3.sd",
            "../../../config-model/src/test/derived/nearestneighbor/test.sd",
            "../../../config-model/src/test/derived/newrank/newrank.sd",
            "../../../config-model/src/test/derived/nuwa/newsindex.sd",
            "../../../config-model/src/test/derived/orderilscripts/orderilscripts.sd",
            "../../../config-model/src/test/derived/position_array/position_array.sd",
            "../../../config-model/src/test/derived/position_attribute/position_attribute.sd",
            "../../../config-model/src/test/derived/position_extra/position_extra.sd",
            "../../../config-model/src/test/derived/position_nosummary/position_nosummary.sd",
            "../../../config-model/src/test/derived/position_summary/position_summary.sd",
            "../../../config-model/src/test/derived/predicate_attribute/predicate_attribute.sd",
            "../../../config-model/src/test/derived/prefixexactattribute/prefixexactattribute.sd",
            "../../../config-model/src/test/derived/rankproperties/rankproperties.sd",
            "../../../config-model/src/test/derived/ranktypes/ranktypes.sd",
            "../../../config-model/src/test/derived/reserved_position/reserved_position.sd",
            "../../../config-model/src/test/derived/streamingjuniper/streamingjuniper.sd",
            "../../../config-model/src/test/derived/streamingstruct/streamingstruct.sd",
            "../../../config-model/src/test/derived/streamingstructdefault/streamingstructdefault.sd",
            "../../../config-model/src/test/derived/structandfieldset/test.sd",
            "../../../config-model/src/test/derived/structanyorder/structanyorder.sd",
            "../../../config-model/src/test/derived/structinheritance/simple.sd",
            //"../../../config-model/src/test/derived/tensor/tensor.sd",
            "../../../config-model/src/test/derived/tokenization/tokenization.sd",
            "../../../config-model/src/test/derived/types/types.sd",
            "../../../config-model/src/test/derived/uri_array/uri_array.sd",
            "../../../config-model/src/test/derived/uri_wset/uri_wset.sd",
            "../../../config-model/src/test/configmodel/types/types.sd",

            "../../../config-model/src/test/examples/arrays.sd",
            "../../../config-model/src/test/examples/arraysweightedsets.sd",  
            "../../../config-model/src/test/examples/attributesettings.sd",  
            "../../../config-model/src/test/examples/attributesexactmatch.sd",  
            "../../../config-model/src/test/examples/casing.sd",  
            "../../../config-model/src/test/examples/comment.sd",  
            "../../../config-model/src/test/examples/documentidinsummary.sd",  
            "../../../config-model/src/test/examples/implicitsummaries_attribute.sd",  
            "../../../config-model/src/test/examples/implicitsummaryfields.sd",  
            "../../../config-model/src/test/examples/incorrectrankingexpressionfileref.sd",  
            "../../../config-model/src/test/examples/indexing.sd",  
            "../../../config-model/src/test/examples/indexing_extra.sd",  
            "../../../config-model/src/test/examples/indexing_input_other_field.sd",  
            "../../../config-model/src/test/examples/indexing_multiline_output_conflict.sd",  
            "../../../config-model/src/test/examples/indexing_summary_changed.sd",  
            "../../../config-model/src/test/examples/indexrewrite.sd",  
            "../../../config-model/src/test/examples/indexsettings.sd",  
            "../../../config-model/src/test/examples/integerindex2attribute.sd",  
            "../../../config-model/src/test/examples/invalidngram1.sd",  
            "../../../config-model/src/test/examples/invalidngram2.sd",  
            "../../../config-model/src/test/examples/invalidngram3.sd",  
            "../../../config-model/src/test/examples/largerankingexpressions/rankexpression.sd",  
            "../../../config-model/src/test/examples/multiplesummaries.sd",  
            "../../../config-model/src/test/examples/nextgen/boldedsummaryfields.sd",  
            "../../../config-model/src/test/examples/nextgen/dynamicsummaryfields.sd",  
            "../../../config-model/src/test/examples/nextgen/extrafield.sd",  
            "../../../config-model/src/test/examples/nextgen/implicitstructtypes.sd",  
            "../../../config-model/src/test/examples/nextgen/simple.sd",  
            "../../../config-model/src/test/examples/nextgen/summaryfield.sd",  
            "../../../config-model/src/test/examples/nextgen/toggleon.sd",  
            "../../../config-model/src/test/examples/nextgen/untransformedsummaryfields.sd",  
            "../../../config-model/src/test/examples/nextgen/unusedfields.sd",  
            "../../../config-model/src/test/examples/nextgen/uri_array.sd",  
            "../../../config-model/src/test/examples/nextgen/uri_simple.sd",  
            "../../../config-model/src/test/examples/nextgen/uri_wset.sd",  
            "../../../config-model/src/test/examples/ngram.sd",  
            "../../../config-model/src/test/examples/outsidedoc.sd",  
            "../../../config-model/src/test/examples/outsidesummary.sd",  
            "../../../config-model/src/test/examples/position_array.sd",  
            "../../../config-model/src/test/examples/position_attribute.sd",  
            "../../../config-model/src/test/examples/position_base.sd",  
            "../../../config-model/src/test/examples/position_extra.sd",  
            "../../../config-model/src/test/examples/position_index.sd",  
            "../../../config-model/src/test/examples/position_summary.sd",  
            "../../../config-model/src/test/examples/rankingexpressionfunction/rankingexpressionfunction.sd",  
            "../../../config-model/src/test/examples/rankingexpressioninfile/rankingexpressioninfile.sd",  
            "../../../config-model/src/test/examples/rankmodifier/literal.sd",  
            "../../../config-model/src/test/examples/rankpropvars.sd",  
            "../../../config-model/src/test/examples/reserved_words_as_field_names.sd",  
            "../../../config-model/src/test/examples/stemmingdefault.sd",  
            "../../../config-model/src/test/examples/stemmingsetting.sd",  
            "../../../config-model/src/test/examples/strange.sd",  
            "../../../config-model/src/test/examples/struct.sd",  
            "../../../config-model/src/test/examples/struct_outside.sd",  
            "../../../config-model/src/test/examples/structanddocumentwithsamenames.sd",  
            "../../../config-model/src/test/examples/structoutsideofdocument.sd",  
            "../../../config-model/src/test/examples/summaryfieldcollision.sd",  
            "../../../config-model/src/test/examples/weightedset-summaryto.sd",  


            /*
             * CUSTOM TESTS
             * */
            "src/test/sdfiles/single/structinfieldset.sd",
            "src/test/sdfiles/single/attributeposition.sd",
        };

        return Arrays.stream(filePaths)
                     .map(path -> DynamicTest.dynamicTest(path, () -> checkFileParses(path)));
    }

    @TestFactory
    Stream<DynamicTest> generateGoodDirectoryTests() {
        String[] directories = new String[] {
            "../../../config-model/src/test/cfg/search/data/travel/schemas/",
            "../../../config-model/src/test/derived/deriver/",
            "../../../config-model/src/test/derived/emptychild/",
            "../../../config-model/src/test/derived/imported_fields_inherited_reference/",
            "../../../config-model/src/test/derived/imported_position_field/",
            "../../../config-model/src/test/derived/imported_position_field_summary/",
            "../../../config-model/src/test/derived/imported_struct_fields/",
            "../../../config-model/src/test/derived/importedfields/",
            "../../../config-model/src/test/derived/inheritance/",
            "../../../config-model/src/test/derived/inheritdiamond/",
            "../../../config-model/src/test/derived/inheritfromgrandparent/",
            "../../../config-model/src/test/derived/inheritfromparent/",
            "../../../config-model/src/test/derived/inheritstruct/",
            "../../../config-model/src/test/derived/namecollision/",
            "../../../config-model/src/test/derived/rankprofileinheritance/",
            "../../../config-model/src/test/derived/schemainheritance/",
            "../../../config-model/src/test/derived/tensor2/",
            "../../../config-model/src/test/derived/twostreamingstructs/",
            "../../../config-model/src/test/derived/rankprofilemodularity/",
            "../../../config-model/src/test/derived/reference_fields/",

            /*
             * CUSTOM TESTS
             */
            "src/test/sdfiles/multi/types/",
            "src/test/sdfiles/multi/bookandmusic/",
        };

        return Arrays.stream(directories)
                     .map(dir -> DynamicTest.dynamicTest(dir, () -> checkDirectoryParses(dir)));
    }

    record BadFileTestCase(String filePath, int expectedErrors) {}

    @TestFactory
    Stream<DynamicTest> generateBadFileTests() {
        BadFileTestCase[] tests = new BadFileTestCase[] {
            new BadFileTestCase("../../../config-model/src/test/derived/inheritfromnull/inheritfromnull.sd", 1),
            new BadFileTestCase("../../../config-model/src/test/derived/structinheritance/bad.sd", 1), // TODO: check that the error is correct
            new BadFileTestCase("src/test/sdfiles/single/rankprofilefuncs.sd", 2),
            new BadFileTestCase("../../../config-model/src/test/derived/function_arguments/test.sd", 3),
            new BadFileTestCase("../../../config-model/src/test/derived/flickr/flickrphotos.sd", 1),
            new BadFileTestCase("../../../config-model/src/test/examples/badparse.sd", 1),
            new BadFileTestCase("../../../config-model/src/test/examples/badstruct.sd", 2),
            new BadFileTestCase("../../../config-model/src/test/examples/documents.sd", 1),
            new BadFileTestCase("../../../config-model/src/test/examples/indexing_invalid_expression.sd", 1),
            new BadFileTestCase("../../../config-model/src/test/examples/invalid-name.sd", 1),
            new BadFileTestCase("../../../config-model/src/test/examples/invalid_sd_construct.sd", 1),
            new BadFileTestCase("../../../config-model/src/test/examples/invalid_sd_junk_at_end.sd", 1),
            new BadFileTestCase("../../../config-model/src/test/examples/invalid_sd_lexical_error.sd", 1), 
            new BadFileTestCase("../../../config-model/src/test/examples/invalid_sd_missing_document.sd", 2),
            new BadFileTestCase("../../../config-model/src/test/examples/invalid_sd_no_closing_bracket.sd", 1), 
            new BadFileTestCase("../../../config-model/src/test/examples/invalidimplicitsummarysource.sd", 1), 
            new BadFileTestCase("../../../config-model/src/test/examples/invalidselfreferringsummary.sd", 1),
            new BadFileTestCase("../../../config-model/src/test/examples/invalidsummarysource.sd", 1),
            new BadFileTestCase("../../../config-model/src/test/examples/stemmingresolver.sd", 1),
            new BadFileTestCase("../../../config-model/src/test/derived/rankingexpression/rankexpression.sd", 7),
            new BadFileTestCase("../../../config-model/src/test/derived/renamedfeatures/foo.sd", 1),

            new BadFileTestCase("../../../config-model/src/test/derived/rankprofiles/rankprofiles.sd", 1), // only throws a warning during vespa deploy, but it is an unresolved reference case.

            new BadFileTestCase("../../../config-model/src/test/derived/slice/test.sd", 2), // TODO: slicing?

            new BadFileTestCase("../../../config-model/src/test/examples/simple.sd", 5), // TODO: unused rank-profile functions should throw errors? Also rank-type doesntexist: ... in field?

            new BadFileTestCase("src/test/sdfiles/single/rankprofilefuncs.sd", 2),
            new BadFileTestCase("src/test/sdfiles/single/onnxmodel.sd", 1),
        };

        return Arrays.stream(tests)
                     .map(testCase -> DynamicTest.dynamicTest(testCase.filePath(), () -> checkFileFails(testCase.filePath(), testCase.expectedErrors())));
    }

}
