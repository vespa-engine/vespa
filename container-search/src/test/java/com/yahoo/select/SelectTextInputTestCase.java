// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.select;

import com.yahoo.language.Language;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.ExactStringItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NearItem;
import com.yahoo.prelude.query.NullItem;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.PhraseSegmentItem;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.search.Query;
import com.yahoo.search.query.QueryTree;
import com.yahoo.search.query.Select;
import com.yahoo.search.query.SelectParser;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the text() operator in SelectParser (JSON query API).
 */
public class SelectTextInputTestCase {

    private SelectParser parser;

    @BeforeEach
    public void setUp() {
        parser = new SelectParser(new ParserEnvironment());
    }

    private QueryTree parseWhere(String where) {
        Select select = new Select(where, "", new Query());
        return parser.parse(new Parsable().setSelect(select));
    }

    private QueryTree parseWhereWithLanguage(String where, Language language) {
        Select select = new Select(where, "", new Query());
        return parser.parse(new Parsable().setSelect(select).setExplicitLanguage(Optional.of(language)));
    }

    private Item getRootItem(String where) {
        return parseWhere(where).getRoot();
    }

    private void assertParseFail(String where, Throwable expectedCause) {
        try {
            parseWhere(where).toString();
            fail("Parse succeeded: " + where);
        } catch (Throwable outer) {
            assertEquals(IllegalInputException.class, outer.getClass());
            assertEquals("Illegal JSON query", outer.getMessage());
            Throwable cause = outer.getCause();
            assertNotNull(cause);
            assertEquals(expectedCause.getClass(), cause.getClass());
            assertEquals(expectedCause.getMessage(), cause.getMessage());
        }
    }

    private static void assertAllChildWordsHaveField(Item root, String expectedField) {
        if (root instanceof CompositeItem composite) {
            for (int i = 0; i < composite.getItemCount(); i++)
                assertAllChildWordsHaveField(composite.getItem(i), expectedField);
        }
        if (root instanceof WordItem word)
            assertEquals(expectedField, word.getIndexName());
    }

    private static void assertAllChildWordsHaveLanguage(Item root, Language expected) {
        if (root instanceof CompositeItem composite) {
            for (int i = 0; i < composite.getItemCount(); i++)
                assertAllChildWordsHaveLanguage(composite.getItem(i), expected);
        }
        assertEquals(expected, root.getLanguage());
    }

    private static WordItem getFirstWord(Item root) {
        if (root instanceof CompositeItem composite) {
            assertFalse(composite.getItemCount() == 0);
            return getFirstWord(composite.getItem(0));
        }
        assertInstanceOf(WordItem.class, root);
        return (WordItem) root;
    }

    @Test
    void containsTextStringParses() {
        Item root = getRootItem("{ \"contains\": [ \"f\", { \"text\": \"a b\" } ] }");
        assertNotNull(root);
    }

    @Test
    void textObjectQueryShapeWorks() {
        Item root = getRootItem("{ \"contains\": [ \"f\", { \"text\": { \"query\": \"a b\" } } ] }");
        assertNotNull(root);
        assertInstanceOf(WeakAndItem.class, root);
        assertEquals(2, ((WeakAndItem) root).getItemCount());
    }

    @Test
    void textObjectQueryWithAttributes() {
        Item root = getRootItem("{ \"contains\": [ \"f\", { \"text\": { \"query\": \"a b\", \"attributes\": { \"grammar\": \"raw\" } } } ] }");
        assertInstanceOf(ExactStringItem.class, root);
    }

    @Test
    void textDefaultsToLinguistics() {
        Item root = getRootItem("{ \"contains\": [ \"f\", { \"text\": \"a b\" } ] }");
        assertInstanceOf(WeakAndItem.class, root);
    }

    @Test
    void defaultIndexPropagation() {
        Item root = getRootItem("{ \"contains\": [ \"title\", { \"text\": \"x y\" } ] }");
        assertAllChildWordsHaveField(root, "title");
    }

    @Test
    void allowEmptyTrueReturnsNullItem() {
        Item root = getRootItem("{ \"contains\": [ \"f\", { \"text\": { \"query\": \"\", \"attributes\": { \"allowEmpty\": true } } } ] }");
        assertInstanceOf(NullItem.class, root);
    }

    @Test
    void allowEmptyFalseEmptyThrows() {
        assertParseFail("{ \"contains\": [ \"f\", { \"text\": \"\" } ] }",
                new IllegalArgumentException("text() requires a non-empty input string. Use allowEmpty annotation to allow empty input."));
    }

    @Test
    void grammarRawYieldsExactStringItem() {
        Item root = getRootItem("{ \"contains\": [ \"f\", { \"text\": { \"query\": \"a b\", \"attributes\": { \"grammar\": \"raw\" } } } ] }");
        assertInstanceOf(ExactStringItem.class, root);
        assertEquals("a b", ((ExactStringItem) root).getWord());
    }

    @Test
    void grammarSegmentMultiTokenYieldsPhraseSegmentItem() {
        Item root = getRootItem("{ \"contains\": [ \"f\", { \"text\": { \"query\": \"hello world\", \"attributes\": { \"grammar\": \"segment\" } } } ] }");
        assertInstanceOf(PhraseSegmentItem.class, root);
        assertTrue(((PhraseSegmentItem) root).getItemCount() >= 1);
    }

    @Test
    void grammarSegmentNonSegmentableFallsBackToWordItem() {
        Item root = getRootItem("{ \"contains\": [ \"f\", { \"text\": { \"query\": \"!!!@\", \"attributes\": { \"grammar\": \"segment\" } } } ] }");
        assertTrue(root instanceof PhraseSegmentItem || root instanceof WordItem);
        if (root instanceof PhraseSegmentItem p)
            assertEquals(1, p.getItemCount());
    }

    @Test
    void grammarCompositeAndProducesAndItem() {
        Item root = getRootItem("{ \"contains\": [ \"f\", { \"text\": { \"query\": \"a b\", \"attributes\": { \"grammar.composite\": \"and\" } } } ] }");
        assertInstanceOf(AndItem.class, root);
    }

    @Test
    void grammarCompositeOrProducesOrItem() {
        Item root = getRootItem("{ \"contains\": [ \"f\", { \"text\": { \"query\": \"a b\", \"attributes\": { \"grammar.composite\": \"or\" } } } ] }");
        assertInstanceOf(OrItem.class, root);
    }

    @Test
    void grammarCompositeWeakAndTargetHits() {
        Item root = getRootItem("{ \"contains\": [ \"f\", { \"text\": { \"query\": \"a b\", \"attributes\": { \"grammar.composite\": \"weakAnd\", \"targetHits\": 50 } } } ] }");
        assertInstanceOf(WeakAndItem.class, root);
        assertEquals(50, ((WeakAndItem) root).getTargetHits());
    }

    @Test
    void grammarCompositeWeakAndTotalTargetHits() {
        Item root = getRootItem("{ \"contains\": [ \"f\", { \"text\": { \"query\": \"a b\", \"attributes\": { \"grammar.composite\": \"weakAnd\", \"totalTargetHits\": 100 } } } ] }");
        assertInstanceOf(WeakAndItem.class, root);
        assertEquals(100, ((WeakAndItem) root).getTotalTargetHits());
    }

    @Test
    void grammarCompositeNearDistance() {
        Item root = getRootItem("{ \"contains\": [ \"f\", { \"text\": { \"query\": \"a b\", \"attributes\": { \"grammar.composite\": \"near\", \"distance\": 5 } } } ] }");
        assertInstanceOf(NearItem.class, root);
        assertEquals(5, ((NearItem) root).getDistance());
    }

    @Test
    void grammarCompositeONearDistance() {
        Item root = getRootItem("{ \"contains\": [ \"f\", { \"text\": { \"query\": \"a b\", \"attributes\": { \"grammar.composite\": \"oNear\", \"distance\": 7 } } } ] }");
        assertInstanceOf(NearItem.class, root);
        assertEquals(7, ((NearItem) root).getDistance());
    }

    @Test
    void explicitLanguageAnnotationSetsFrench() {
        Item root = getRootItem("{ \"contains\": [ \"f\", { \"text\": { \"query\": \"hello\", \"attributes\": { \"language\": \"fr\" } } } ] }");
        assertEquals(Language.FRENCH, root.getLanguage());
    }

    @Test
    void queryLevelExplicitLanguageUsed() {
        String where = "{ \"contains\": [ \"f\", { \"text\": \"hello\" } ] }";
        QueryTree tree = parseWhereWithLanguage(where, Language.JAPANESE);
        Item root = tree.getRoot();
        assertEquals(Language.JAPANESE, root.getLanguage());
    }

    @Test
    void localAnnotationOverridesQueryLevel() {
        String where = "{ \"contains\": [ \"f\", { \"text\": { \"query\": \"hello\", \"attributes\": { \"language\": \"en\" } } } ] }";
        QueryTree tree = parseWhereWithLanguage(where, Language.JAPANESE);
        Item root = tree.getRoot();
        assertEquals(Language.ENGLISH, root.getLanguage());
    }

    @Test
    void grammarSegmentLanguageRecursive() {
        Item root = getRootItem("{ \"contains\": [ \"f\", { \"text\": { \"query\": \"hello world\", \"attributes\": { \"grammar\": \"segment\", \"language\": \"ja\" } } } ] }");
        assertAllChildWordsHaveLanguage(root, Language.JAPANESE);
    }

    @Test
    void stemPropagates() {
        Item root = getRootItem("{ \"contains\": [ \"f\", { \"text\": { \"query\": \"a\", \"attributes\": { \"stem\": false } } } ] }");
        assertTrue(getFirstWord(root).isStemmed());
    }

    @Test
    void rankedPropagates() {
        Item root = getRootItem("{ \"contains\": [ \"f\", { \"text\": { \"query\": \"a\", \"attributes\": { \"ranked\": false } } } ] }");
        assertFalse(getFirstWord(root).isRanked());
    }

    @Test
    void filterPropagates() {
        Item root = getRootItem("{ \"contains\": [ \"f\", { \"text\": { \"query\": \"a\", \"attributes\": { \"filter\": true } } } ] }");
        assertTrue(getFirstWord(root).isFilter());
    }

    @Test
    void normalizeCasePropagates() {
        Item root = getRootItem("{ \"contains\": [ \"f\", { \"text\": { \"query\": \"a\", \"attributes\": { \"normalizeCase\": false } } } ] }");
        assertTrue(getFirstWord(root).isLowercased());
    }

    @Test
    void accentDropPropagates() {
        Item root = getRootItem("{ \"contains\": [ \"f\", { \"text\": { \"query\": \"a\", \"attributes\": { \"accentDrop\": false } } } ] }");
        assertFalse(getFirstWord(root).isNormalizable());
    }

    @Test
    void usePositionDataPropagates() {
        Item root = getRootItem("{ \"contains\": [ \"f\", { \"text\": { \"query\": \"a\", \"attributes\": { \"usePositionData\": false } } } ] }");
        assertFalse(getFirstWord(root).usePositionData());
    }

    @Test
    void segmentChildrenGetAnnotations() {
        Item root = getRootItem("{ \"contains\": [ \"f\", { \"text\": { \"query\": \"hello world\", \"attributes\": { \"grammar\": \"segment\", \"stem\": false } } } ] }");
        assertInstanceOf(PhraseSegmentItem.class, root);
        WordItem first = getFirstWord(root);
        assertTrue(first.isStemmed(), "stem:false should propagate to segment children");
    }

    @Test
    void rawAnnotationsDocumented() {
        Item root = getRootItem("{ \"contains\": [ \"f\", { \"text\": { \"query\": \"a\", \"attributes\": { \"grammar\": \"raw\", \"stem\": false } } } ] }");
        assertInstanceOf(ExactStringItem.class, root);
        assertFalse(((ExactStringItem) root).isStemmed(), "grammar:raw does NOT propagate stem (and other annotations) by design");
    }

    @Test
    void textArrayThrows() {
        assertParseFail("{ \"contains\": [ \"f\", { \"text\": [ \"a b\" ] } ] }",
                new IllegalArgumentException("text() does not support array arguments; use a string or an object with a 'query' field."));
    }

    @Test
    void textChildrenThrows() {
        assertParseFail("{ \"contains\": [ \"f\", { \"text\": { \"children\": [ \"a b\" ] } } ] }",
                new IllegalArgumentException("text() does not support 'children'; use a 'query' string field instead."));
    }

    @Test
    void textObjectWithoutQueryThrows() {
        assertParseFail("{ \"contains\": [ \"f\", { \"text\": { } } ] }",
                new IllegalArgumentException("text() object form requires a 'query' string field."));
    }

    @Test
    void textQueryNonStringThrows() {
        assertParseFail("{ \"contains\": [ \"f\", { \"text\": { \"query\": 42 } } ] }",
                new IllegalArgumentException("text().query must be a string, got LONG"));
    }

    @Test
    void unknownGrammarThrows() {
        assertParseFail("{ \"contains\": [ \"f\", { \"text\": { \"query\": \"a\", \"attributes\": { \"grammar\": \"invalid\" } } } ] }",
                new IllegalArgumentException("No query type 'invalid'"));
    }

    @Test
    void textOutsideContainsFails() {
        assertParseFail("{ \"text\": \"hello\" }",
                new IllegalArgumentException("Expected and, and_not, call, contains, equals, in, matches, not, or or range, got text."));
    }
}
