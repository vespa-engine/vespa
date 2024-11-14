package ai.vespa.schemals;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;

import ai.vespa.schemals.parser.SchemaParserLexer;
import ai.vespa.schemals.parser.indexinglanguage.IndexingParserLexer;
import ai.vespa.schemals.parser.rankingexpression.RankingExpressionParserLexer;
import ai.vespa.schemals.parser.grouping.GroupingParserLexer;

import com.vladsch.flexmark.parser.Parser;
import com.yahoo.schema.parser.SchemaParserConstants;
import com.yahoo.vespa.indexinglanguage.parser.IndexingParserConstants;
import com.yahoo.searchlib.rankingexpression.parser.RankingExpressionParserConstants;
import com.yahoo.search.grouping.request.parser.GroupingParserConstants;


/**
 * Tests that the set of tokens declared in JavaCC parsers are also present in CongoCC parsers.
 */
public class ParserTokensTest {
    // These are special
    public static Set<String> javaCCSpecialTokens = Set.of(
        "DEFAULT", // Lexical State in JavaCC parser
        "SEARCHLIB_SKIP", // represents white space, handled differently in CongoCC
        "SINGLE_LINE_COMMENT", // not a regular token
        "COMMENT", // not a regular token
        // Below are anonymous tokens, only there to help defining ranking expression rules. CongoCC ignores their names.
        // Schema:
        "BRACE_SL_LEVEL_1",
        "BRACE_SL_LEVEL_2",
        "BRACE_SL_LEVEL_3",
        "BRACE_SL_CONTENT",
        "BRACE_ML_LEVEL_1",
        "BRACE_ML_LEVEL_2",
        "BRACE_ML_LEVEL_3",
        "BRACE_ML_CONTENT",
        // Ranking expression:
        "DECIMAL",
        "EXPONENT",
        "HEX",
        "OCTAL"
    );

    private List<String> findMissingTokens(Field[] javaCCFields, Set<String> congoCCTokenStrings) {
        List<String> missing = new ArrayList<>();

        for (Field field : javaCCFields) {
            if (!field.getType().equals(int.class)) continue;

            String tokenName = field.getName();
            if (javaCCSpecialTokens.contains(tokenName)) continue;

            if (!congoCCTokenStrings.contains(tokenName))missing.add(tokenName);
        }
        return missing;
    }

    @Test
    public void testSchemaTokenList() {
        Field[] javaCCFields = SchemaParserConstants.class.getDeclaredFields();

        Set<String> congoCCTokenStrings = new HashSet<>();

        for (var tokenType : SchemaParserLexer.getRegularTokens()) {
            congoCCTokenStrings.add(tokenType.toString());
        }

        List<String> missing = findMissingTokens(javaCCFields, congoCCTokenStrings);

        assertEquals(0, missing.size(), "Missing schema tokens in CongoCC: " + String.join(", ", missing));
    }

    @Test
    public void testIndexingTokenList() {
        Field[] javaCCFields = IndexingParserConstants.class.getDeclaredFields();

        Set<String> congoCCTokenStrings = new HashSet<>();

        for (var tokenType : IndexingParserLexer.getRegularTokens()) {
            congoCCTokenStrings.add(tokenType.toString());
        }

        List<String> missing = findMissingTokens(javaCCFields, congoCCTokenStrings);
        assertEquals(0, missing.size(), "Missing indexing tokens in CongoCC: " + String.join(", ", missing));
    }

    @Test
    public void testRankingExpressionTokenList() {
        Field[] javaCCFields = RankingExpressionParserConstants.class.getDeclaredFields();

        Set<String> congoCCTokenStrings = new HashSet<>();

        for (var tokenType : RankingExpressionParserLexer.getRegularTokens()) {
            congoCCTokenStrings.add(tokenType.toString());
        }

        List<String> missing = findMissingTokens(javaCCFields, congoCCTokenStrings);
        assertEquals(0, missing.size(), "Missing ranking expression tokens in CongoCC: " + String.join(", ", missing));
    }

    @Test
    public void testVespaGroupingTokenList() {
        Field[] javaCCFields = GroupingParserConstants.class.getDeclaredFields();

        Set<String> congoCCTokenStrings = new HashSet<>();

        for (var tokenType : GroupingParserLexer.getRegularTokens()) {
            congoCCTokenStrings.add(tokenType.toString());
        }

        List<String> missing = findMissingTokens(javaCCFields, congoCCTokenStrings);
        assertEquals(0, missing.size(), "Missing ranking expression tokens in CongoCC: " + String.join(", ", missing));
    }
}
