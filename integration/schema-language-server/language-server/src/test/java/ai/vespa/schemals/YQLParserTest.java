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

    void checkQueryParses(int expectedErrors, String input) throws Exception {
        try {
            var parseResult = parseString(input);
            String testMessage = "For input: " + input + Utils.constructDiagnosticMessage(parseResult.diagnostics(), 1);
            assertEquals(expectedErrors, Utils.countErrors(parseResult.diagnostics()), testMessage);
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

            // From docs /en/reference/query-language-reference.html
            "select * from sources * where text contains \"blues\"",
            "select price,isbn from sources * where title contains \"madonna\"",
            "select * from sources * where title contains \"madonna\"",
            "select * from music where title contains \"madonna\"",

            "select * from music where 500 >= price",
            "select * from music where range(fieldname, 0, 5000000000L)",
            "select * from music where (range(year, 2000, Infinity))",
            "select * from music where alive = true",
            "select * from music where title contains \"madonna\" and title contains \"saint\"",
            "select * from music where title contains \"madonna\" or title contains \"saint\"",
            "select * from music where title contains \"madonna\" and !(title contains \"saint\")",
            "select * from music where text contains phrase(\"st\", \"louis\", \"blues\")",
            "select * from music where persons contains sameElement(first_name contains 'Joe', last_name contains 'Smith', year_of_birth < 1940)",
            "select * from music where identities contains sameElement(key contains 'father', value.first_name contains 'Joe', value.last_name contains 'Smith', value.year_of_birth < 1940)",
            // "select * from music where gradparentStruct.parentStruct.childField contains 'madonna'",
            "select * from music where fieldName contains equiv(\"A\",\"B\")",
            "select * from music where myUrlField contains uri(\"vespa.ai/foo\")",
            "select * from music where myStringAttribute contains ({prefixLength:1, maxEditDistance:2}fuzzy(\"parantesis\"))",
            "select * from music where attribute_field matches \"mado[n]+a\"",
            "select * from sources * where userInput(@animal)",
            "select * from sources * where weakAnd(default contains \"panda\")",
            "select * from sources * where vendor contains \"brick and mortar\" AND price < 50 AND userQuery()",
            "select * from music where rank(a contains \"A\", b contains \"B\", c contains \"C\")",
            "select * from music where rank(nearestNeighbor(field, queryVector), a contains \"A\", b contains \"B\", c contains \"C\")",
            // "select * from music where integer_field in (10, 20, 30)",
            // "select * from music where string_field in ('germany', 'france', 'norway')",
            // "select * from music where integer_field in (@integer_values)",
            // "select * from music where string_field in (@string_values)",
            // "select * from music where dotProduct(description, {\"a\":1, \"b\":2})",
            // "select * from music where weightedSet(description, {\"a\":1, \"b\":2})",
            // "select * from music where wand(description, [[11,1], [37,2]])",
            // "select * from music where ({scoreThreshold: 0.13, targetHits: 7}wand(description, {\"a\":1, \"b\":2}))",
            "select * from music where weakAnd(a contains \"A\", b contains \"B\")",
            "select * from music where ({targetHits: 7}weakAnd(a contains \"A\", b contains \"B\"))",
            "select * from music where geoLocation(myfieldname, 63.5, 10.5, \"200 km\")",
            "select * from music where ({targetHits: 10}nearestNeighbor(doc_vector, query_vector))&input.query(query_vector)=[3,5,7]",
            "select * from sources * where bar contains \"a\" and nonEmpty(bar contains \"bar\" and foo contains @foo)",
            // "select * from music where predicate(predicate_field,{\"gender\":\"Female\"},{\"age\":20L})",
            // "select * from music where predicate(predicate_field,0,{\"age\":20L})",
            "select * from music where title contains \"madonna\" order by price asc, releasedate desc",
            "select * from music where title contains \"madonna\" order by {function: \"uca\", locale: \"en_US\", strength: \"IDENTICAL\"}other desc, {function: \"lowercase\"}something",
            "select * from music where title contains \"madonna\" limit 31 offset 29",
            "select * from music where title contains \"madonna\" timeout 70",
            "select * from music where userInput(@userinput)",
            "select * from music where text contains ({distance: 5}near(\"a\", \"b\")) and text contains ({distance:2}near(\"c\", \"d\"))",
        };


        return Arrays.stream(queries)
                     .map(query -> DynamicTest.dynamicTest(query, () -> checkQueryParses(0, query)));
    }

    private record TestWithError(int expectedErrors, String query) {}

    @TestFactory
    Stream<DynamicTest> InvalidQuery() throws Exception {
        var queries = new TestWithError[] {
            new TestWithError(1, "seletc *"),
        };

        return Arrays.stream(queries)
                     .map(queryTest -> DynamicTest.dynamicTest(
                        queryTest.query() + " Expects " + queryTest.expectedErrors() + " errors",
                        () -> checkQueryParses(queryTest.expectedErrors(), queryTest.query())
                      ));
    }


}
