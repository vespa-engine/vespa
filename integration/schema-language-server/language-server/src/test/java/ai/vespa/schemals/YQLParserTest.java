package ai.vespa.schemals;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.schemadocument.YQLDocument;
import ai.vespa.schemals.schemadocument.YQLDocument.ParseResult;

import ai.vespa.schemals.testutils.*;

public class YQLParserTest {

    ParseResult parseString(String input, String fileName) throws Exception {
        ParseContext context = Utils.createTestContext(input, fileName);
        context.useVespaGroupingIdentifiers();
        return YQLDocument.parseContent(context);
    }

    ParseResult parseString(String input) throws Exception {
        return parseString(input, "/tmp/yql");
    }

    void checkQueryParses(String input) throws Exception {
        try {
            var parseResult = parseString(input);
            String testMessage = "For input: " + input + Utils.constructDiagnosticMessage(parseResult.diagnostics(), 1);
            assertEquals(0, Utils.countErrors(parseResult.diagnostics()), testMessage);
        } catch (Exception e) {
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            e.printStackTrace(printWriter);
            throw new Exception(input + "\n" + e.getMessage() + "\n" + stringWriter.toString());
        }
    }

    @TestFactory
    Stream<DynamicTest> generateGoodTests() {
        String[] queries = new String[] {
            "select * from music",
            "select * from sources * where range(title, 0.0, 500.0)", // /container-search/src/test/java/com/yahoo/select/SelectTestCase.java

            // From docs /en/query-language.html
            "select * from doc where true",
            "select * from doc where title contains \"ranking\"",
            "select * from doc where default contains \"ranking\"",
            "select * from doc where true order by term_count desc",
            "select * from doc where true limit 15 offset 5",
            "select * from doc where true limit 0 | all( group( fixedwidth(term_count,100) ) each( output( avg(term_count) ) ) )",
            "select * from doc where last_updated > 1646167144",
            "select * from doc where term_count = 1403",
            "select * from doc where default contains phrase(\"question\",\"answering\")",
            "select * from doc where true timeout 100",
            "select * from doc where namespace matches \"op-*\"",
            "select * from doc where is_public = true",
            "select * from doc where my_map contains sameElement(key contains \"Coldplay\", value > 10)",
            "select * from music where artist matches \"Meta..ica\"",
        };


        return Arrays.stream(queries)
                     .map(query -> DynamicTest.dynamicTest(query, () -> checkQueryParses(query)));
    }

    @Test
    void InvalidQuery() throws Exception {
        var result = parseString("seltc *");

        assertEquals(1, result.diagnostics().size());
    }


}
