// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.prelude.query;

import ai.vespa.json.Json;
import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ToProtobuf class and toProtobuf() method in all Item subclasses
 */
public class ToProtobufTest {

    private String toJson(SearchProtocol.QueryTreeItem item) throws InvalidProtocolBufferException {
        return JsonFormat.printer().print(item);
    }

    private String toJson(SearchProtocol.TermItemProperties props) throws InvalidProtocolBufferException {
        return JsonFormat.printer().print(props);
    }

    private void assertJsonEquals(String actualJson, String expectedJson) {
        assertTrue(Json.equivalent(Json.of(actualJson), expectedJson, true),
                   "Expected JSON: " + expectedJson + "\nActual JSON: " + actualJson);
    }

    @Test
    void testConvertFromQueryWithNullItem() {
        assertThrows(IllegalArgumentException.class, () -> {
            ToProtobuf.convertFromQuery(null);
        });
    }

    @Test
    void testConvertFromQueryWithWordItem() throws InvalidProtocolBufferException {
        WordItem word = new WordItem("test", "myindex");
        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(word);
        assertNotNull(result);
        String json = toJson(result);
        assertJsonEquals(json, "{\"itemWordTerm\": {\"word\": \"test\", \"properties\": {\"index\": \"myindex\"}}}");
    }

    @Test
    void testConvertFromQueryWithAndItem() throws InvalidProtocolBufferException {
        AndItem and = new AndItem();
        and.addItem(new WordItem("foo", "myindex"));
        and.addItem(new WordItem("bar", "myindex"));
        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(and);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemAnd": {
                "children": [
                  {"itemWordTerm": {"word": "foo", "properties": {"index": "myindex"}}},
                  {"itemWordTerm": {"word": "bar", "properties": {"index": "myindex"}}}
                ]
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testBuildTermPropertiesWithDefaultValues() throws InvalidProtocolBufferException {
        WordItem word = new WordItem("test");
        SearchProtocol.TermItemProperties props = ToProtobuf.buildTermProperties(word);
        assertNotNull(props);
        String json = toJson(props);
        assertFalse(props.hasItemWeight());
        assertFalse(props.getDoNotRank());
        assertFalse(props.getDoNotUsePositionData());
        assertFalse(props.getDoNotHighlight());
        assertFalse(props.getIsSpecialToken());
    }

    @Test
    void testBuildTermPropertiesWithIndex() throws InvalidProtocolBufferException {
        WordItem word = new WordItem("test", "myindex");
        SearchProtocol.TermItemProperties props = ToProtobuf.buildTermProperties(word);
        assertNotNull(props);
        String json = toJson(props);
        assertEquals("myindex", props.getIndex());
        assertJsonEquals(json, "{\"index\": \"myindex\"}");
    }

    @Test
    void testBuildTermPropertiesWithWeight() throws InvalidProtocolBufferException {
        WordItem word = new WordItem("test", "myindex");
        word.setWeight(200);
        SearchProtocol.TermItemProperties props = ToProtobuf.buildTermProperties(word);
        assertNotNull(props);
        String json = toJson(props);
        assertEquals(200, props.getItemWeight());
        assertJsonEquals(json, "{\"index\": \"myindex\", \"itemWeight\": 200}");
    }

    @Test
    void testBuildTermPropertiesWithUniqueId() throws InvalidProtocolBufferException {
        WordItem word = new WordItem("test", "myindex");
        word.setUniqueID(42);
        SearchProtocol.TermItemProperties props = ToProtobuf.buildTermProperties(word);
        assertNotNull(props);
        String json = toJson(props);
        assertEquals(42, props.getUniqueId());
        assertJsonEquals(json, "{\"index\": \"myindex\", \"uniqueId\": 42}");
    }

    @Test
    void testBuildTermPropertiesWithRankedFalse() throws InvalidProtocolBufferException {
        WordItem word = new WordItem("test", "myindex");
        word.setRanked(false);
        SearchProtocol.TermItemProperties props = ToProtobuf.buildTermProperties(word);
        assertNotNull(props);
        String json = toJson(props);
        assertTrue(props.getDoNotRank());
        assertJsonEquals(json, "{\"index\": \"myindex\", \"doNotRank\": true}");
    }

    @Test
    void testBuildTermPropertiesWithPositionDataFalse() throws InvalidProtocolBufferException {
        WordItem word = new WordItem("test", "myindex");
        word.setPositionData(false);
        SearchProtocol.TermItemProperties props = ToProtobuf.buildTermProperties(word);
        assertNotNull(props);
        String json = toJson(props);
        assertTrue(props.getDoNotUsePositionData());
        assertJsonEquals(json, "{\"index\": \"myindex\", \"doNotUsePositionData\": true}");
    }

    @Test
    void testBuildTermPropertiesWithFilterTrue() throws InvalidProtocolBufferException {
        WordItem word = new WordItem("test", "myindex");
        word.setFilter(true);
        SearchProtocol.TermItemProperties props = ToProtobuf.buildTermProperties(word);
        assertNotNull(props);
        String json = toJson(props);
        assertTrue(props.getDoNotHighlight());
        assertJsonEquals(json, "{\"index\": \"myindex\", \"doNotHighlight\": true}");
    }

    @Test
    void testBuildTermPropertiesWithSpecialToken() throws InvalidProtocolBufferException {
        WordItem word = new WordItem("test", "myindex");
        word.setFromSpecialToken(true);
        SearchProtocol.TermItemProperties props = ToProtobuf.buildTermProperties(word);
        assertNotNull(props);
        String json = toJson(props);
        assertTrue(props.getIsSpecialToken());
        assertJsonEquals(json, "{\"index\": \"myindex\", \"isSpecialToken\": true}");
    }

    @Test
    void testBuildTermPropertiesWithAllProperties() throws InvalidProtocolBufferException {
        WordItem word = new WordItem("test", "myindex");
        word.setWeight(150);
        word.setUniqueID(99);
        word.setRanked(false);
        word.setPositionData(false);
        word.setFilter(true);
        word.setFromSpecialToken(true);

        SearchProtocol.TermItemProperties props = ToProtobuf.buildTermProperties(word);

        assertNotNull(props);
        String json = toJson(props);
        assertEquals("myindex", props.getIndex());
        assertEquals(150, props.getItemWeight());
        assertEquals(99, props.getUniqueId());
        assertTrue(props.getDoNotRank());
        assertTrue(props.getDoNotUsePositionData());
        assertTrue(props.getDoNotHighlight());
        assertTrue(props.getIsSpecialToken());
        String expected = """
            {
              "index": "myindex",
              "itemWeight": 150,
              "uniqueId": 99,
              "doNotRank": true,
              "doNotUsePositionData": true,
              "doNotHighlight": true,
              "isSpecialToken": true
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testBuildTermPropertiesWithNonIndexedItem() throws InvalidProtocolBufferException {
        OrItem or = new OrItem();
        SearchProtocol.TermItemProperties props = ToProtobuf.buildTermProperties(or);
        assertNotNull(props);
        String json = toJson(props);
        assertJsonEquals(json, "{}");
    }

    @Test
    void testBuildTermPropertiesWithPhraseItem() throws InvalidProtocolBufferException {
        PhraseItem phrase = new PhraseItem("myindex");
        phrase.addItem(new WordItem("foo"));
        phrase.addItem(new WordItem("bar"));
        phrase.setWeight(250);

        SearchProtocol.TermItemProperties props = ToProtobuf.buildTermProperties(phrase);

        assertNotNull(props);
        String json = toJson(props);
        assertEquals("myindex", props.getIndex());
        assertEquals(250, props.getItemWeight());
        assertJsonEquals(json, "{\"index\": \"myindex\", \"itemWeight\": 250}");
    }

    @Test
    void testConvertFromQueryWithOrItem() throws InvalidProtocolBufferException {
        OrItem or = new OrItem();
        or.addItem(new WordItem("foo", "myindex"));
        or.addItem(new WordItem("bar", "myindex"));
        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(or);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemOr": {
                "children": [
                  {"itemWordTerm": {"word": "foo", "properties": {"index": "myindex"}}},
                  {"itemWordTerm": {"word": "bar", "properties": {"index": "myindex"}}}
                ]
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithNestedComposite() throws InvalidProtocolBufferException {
        AndItem and = new AndItem();
        OrItem or = new OrItem();
        or.addItem(new WordItem("foo", "myindex"));
        or.addItem(new WordItem("bar", "myindex"));
        and.addItem(or);
        and.addItem(new WordItem("baz", "myindex"));

        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(and);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemAnd": {
                "children": [
                  {
                    "itemOr": {
                      "children": [
                        {"itemWordTerm": {"word": "foo", "properties": {"index": "myindex"}}},
                        {"itemWordTerm": {"word": "bar", "properties": {"index": "myindex"}}}
                      ]
                    }
                  },
                  {"itemWordTerm": {"word": "baz", "properties": {"index": "myindex"}}}
                ]
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithPhraseItem() throws InvalidProtocolBufferException {
        PhraseItem phrase = new PhraseItem("myindex");
        phrase.addItem(new WordItem("foo"));
        phrase.addItem(new WordItem("bar"));

        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(phrase);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemPhrase": {
                "properties": {"index": "myindex"},
                "children": [
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "foo"}},
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "bar"}}
                ]
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithPrefixItem() throws InvalidProtocolBufferException {
        PrefixItem prefix = new PrefixItem("test", "myindex");
        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(prefix);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemPrefixTerm": {
                "word": "test",
                "properties": {"index": "myindex"}
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithRankItem() throws InvalidProtocolBufferException {
        RankItem rank = new RankItem();
        rank.addItem(new WordItem("foo", "myindex"));
        rank.addItem(new WordItem("bar", "myindex"));

        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(rank);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemRank": {
                "children": [
                  {"itemWordTerm": {"word": "foo", "properties": {"index": "myindex"}}},
                  {"itemWordTerm": {"word": "bar", "properties": {"index": "myindex"}}}
                ]
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testRootItemThrowsUnsupportedOperation() {
        RootItem root = new RootItem(new WordItem("test", "myindex"));
        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class, () -> {
            ToProtobuf.convertFromQuery(root);
        });
        assertTrue(exception.getMessage().contains("should not be serialized"));
    }

    @Test
    void testNullItemThrowsIllegalState() {
        NullItem nullItem = new NullItem();
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            ToProtobuf.convertFromQuery(nullItem);
        });
        assertTrue(exception.getMessage().contains("NullItem was attempted serialized"));
    }

    @Test
    void testPureWeightedStringThrowsUnsupportedOperation() {
        PureWeightedString pureWeighted = new PureWeightedString("test", 100);
        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class, () -> {
            ToProtobuf.convertFromQuery(pureWeighted);
        });
        assertTrue(exception.getMessage().contains("should not serialize itself"));
    }

    @Test
    void testPureWeightedIntegerThrowsUnsupportedOperation() {
        PureWeightedInteger pureWeighted = new PureWeightedInteger(42, 100);
        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class, () -> {
            ToProtobuf.convertFromQuery(pureWeighted);
        });
        assertTrue(exception.getMessage().contains("should not serialize itself"));
    }

    @Test
    void testConvertFromQueryWithNotItem() throws InvalidProtocolBufferException {
        NotItem not = new NotItem();
        not.addPositiveItem(new WordItem("foo", "myindex"));
        not.addNegativeItem(new WordItem("bar", "myindex"));

        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(not);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemAndNot": {
                "children": [
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "foo"}},
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "bar"}}
                ]
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithWeakAndItem() throws InvalidProtocolBufferException {
        WeakAndItem weakAnd = new WeakAndItem();
        weakAnd.addItem(new WordItem("foo", "myindex"));
        weakAnd.addItem(new WordItem("bar", "myindex"));

        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(weakAnd);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemWeakAnd": {
                "targetNumHits": 100,
                "children": [
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "foo"}},
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "bar"}}
                ]
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithSameElementItem() throws InvalidProtocolBufferException {
        SameElementItem sameElement = new SameElementItem("myfield");
        sameElement.addItem(new WordItem("foo"));
        sameElement.addItem(new WordItem("bar"));

        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(sameElement);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemSameElement": {
                "properties": {"index": "myfield"},
                "children": [
                  {"itemWordTerm": {"properties": {}, "word": "foo"}},
                  {"itemWordTerm": {"properties": {}, "word": "bar"}}
                ]
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithNearItem() throws InvalidProtocolBufferException {
        NearItem near = new NearItem();
        near.addItem(new WordItem("foo", "myindex"));
        near.addItem(new WordItem("bar", "myindex"));

        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(near);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemNear": {
                "distance": 2,
                "children": [
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "foo"}},
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "bar"}}
                ]
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithONearItem() throws InvalidProtocolBufferException {
        ONearItem onear = new ONearItem();
        onear.addItem(new WordItem("foo", "myindex"));
        onear.addItem(new WordItem("bar", "myindex"));

        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(onear);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemOnear": {
                "distance": 2,
                "children": [
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "foo"}},
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "bar"}}
                ]
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithEquivItem() throws InvalidProtocolBufferException {
        EquivItem equiv = new EquivItem();
        equiv.addItem(new WordItem("foo", "myindex"));
        equiv.addItem(new WordItem("bar", "myindex"));

        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(equiv);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemEquiv": {
                "properties": {},
                "children": [
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "foo"}},
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "bar"}}
                ]
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithPhraseSegmentItem() throws InvalidProtocolBufferException {
        PhraseSegmentItem phraseSegment = new PhraseSegmentItem("test", false, false);
        phraseSegment.addItem(new WordItem("foo"));
        phraseSegment.addItem(new WordItem("bar"));
        phraseSegment.setIndexName("myindex");

        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(phraseSegment);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemPhrase": {
                "properties": {"index": "myindex"},
                "children": [
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "foo"}},
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "bar"}}
                ]
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithAndSegmentItem() throws InvalidProtocolBufferException {
        AndSegmentItem andSegment = new AndSegmentItem("test", false, false);
        andSegment.addItem(new WordItem("foo"));
        andSegment.addItem(new WordItem("bar"));
        andSegment.setIndexName("myindex");

        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(andSegment);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemAnd": {
                "children": [
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "foo"}},
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "bar"}}
                ]
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithWeightedSetItem() throws InvalidProtocolBufferException {
        WeightedSetItem weightedSet = new WeightedSetItem("myindex");
        weightedSet.addToken("foo", 100);
        weightedSet.addToken("bar", 200);

        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(weightedSet);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemWeightedSetOfString": {
                "properties": {},
                "weightedStrings": [
                  {"weight": 200, "value": "bar"},
                  {"weight": 100, "value": "foo"}
                ]
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithDotProductItem() throws InvalidProtocolBufferException {
        DotProductItem dotProduct = new DotProductItem("myindex");
        dotProduct.addToken("foo", 100);
        dotProduct.addToken("bar", 200);

        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(dotProduct);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemDotProductOfString": {
                "properties": {},
                "weightedStrings": [
                  {"weight": 200, "value": "bar"},
                  {"weight": 100, "value": "foo"}
                ]
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithWandItem() throws InvalidProtocolBufferException {
        WandItem wand = new WandItem("myindex", 10);
        wand.addToken("foo", 100);
        wand.addToken("bar", 200);

        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(wand);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemStringWand": {
                "properties": {},
                "targetNumHits": 10,
                "thresholdBoostFactor": 1.0,
                "weightedStrings": [
                  {"weight": 200, "value": "bar"},
                  {"weight": 100, "value": "foo"}
                ]
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithSuffixItem() throws InvalidProtocolBufferException {
        SuffixItem suffix = new SuffixItem("test");
        suffix.setIndexName("myindex");
        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(suffix);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemSuffixTerm": {
                "word": "test",
                "properties": {"index": "myindex"}
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithSubstringItem() throws InvalidProtocolBufferException {
        SubstringItem substring = new SubstringItem("test");
        substring.setIndexName("myindex");
        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(substring);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemSubstringTerm": {
                "word": "test",
                "properties": {"index": "myindex"}
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithRegExpItem() throws InvalidProtocolBufferException {
        RegExpItem regexp = new RegExpItem("myindex", true, "test.*");
        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(regexp);
        assertNotNull(result);
        String json = toJson(result);
        assertJsonEquals(json, "{\"itemRegexp\": {\"properties\": {\"index\": \"myindex\"}, \"regexp\": \"test.*\"}}");
    }

    @Test
    void testConvertFromQueryWithIntItem() throws InvalidProtocolBufferException {
        IntItem intItem = new IntItem("[10;20]", "myindex");
        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(intItem);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemIntegerRangeTerm": {
                "properties": {"index": "myindex"},
                "lowerLimit": "10",
                "upperLimit": "20"
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithRangeItem() throws InvalidProtocolBufferException {
        RangeItem range = new RangeItem(10, 20, "myindex");
        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(range);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemIntegerRangeTerm": {
                "properties": {"index": "myindex"},
                "lowerLimit": "10",
                "upperLimit": "20"
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithNumericInItem() throws InvalidProtocolBufferException {
        NumericInItem numericIn = new NumericInItem("myindex");
        numericIn.addToken(42L);
        numericIn.addToken(99L);

        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(numericIn);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemNumericIn": {
                "properties": {},
                "numbers": ["42", "99"]
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithStringInItem() throws InvalidProtocolBufferException {
        StringInItem stringIn = new StringInItem("myindex");
        stringIn.addToken("foo");
        stringIn.addToken("bar");

        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(stringIn);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemStringIn": {
                "properties": {},
                "words": ["bar", "foo"]
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithBoolItem() throws InvalidProtocolBufferException {
        BoolItem bool = new BoolItem(true, "myindex");
        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(bool);
        assertNotNull(result);
        String json = toJson(result);
        assertJsonEquals(json, "{\"itemWordTerm\": {\"properties\": {\"index\": \"myindex\"}, \"word\": \"true\"}}");
    }

    @Test
    void testConvertFromQueryWithFuzzyItem() throws InvalidProtocolBufferException {
        FuzzyItem fuzzy = new FuzzyItem("myindex", true, "test", 2, 0, false);
        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(fuzzy);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemFuzzy": {
                "properties": {"index": "myindex"},
                "word": "test",
                "maxEditDistance": 2
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithExactStringItem() throws InvalidProtocolBufferException {
        ExactStringItem exactString = new ExactStringItem("test");
        exactString.setIndexName("myindex");
        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(exactString);
        assertNotNull(result);
        String json = toJson(result);
        assertJsonEquals(json, "{\"itemExactstringTerm\": {\"properties\": {\"index\": \"myindex\"}, \"word\": \"test\"}}");
    }

    @Test
    void testConvertFromQueryWithMarkerWordItem() throws InvalidProtocolBufferException {
        MarkerWordItem marker = MarkerWordItem.createStartOfHost("myindex");
        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(marker);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemWordTerm": {
                "word": "^",
                "properties": {"index": "myindex"}
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithTrueItem() throws InvalidProtocolBufferException {
        TrueItem trueItem = new TrueItem();
        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(trueItem);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemTrue": {}
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithFalseItem() throws InvalidProtocolBufferException {
        FalseItem falseItem = new FalseItem();
        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(falseItem);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemFalse": {}
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithWordAlternativesItem() throws InvalidProtocolBufferException {
        WordAlternativesItem alternatives = new WordAlternativesItem("myindex", false, null,
                java.util.List.of(
                    new WordAlternativesItem.Alternative("foo", 1.0),
                    new WordAlternativesItem.Alternative("bar", 0.8)
                ));
        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(alternatives);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemWordAlternatives": {
                "properties": {"index": "myindex"},
                "weightedStrings": [
                  {"weight": 80, "value": "bar"},
                  {"weight": 100, "value": "foo"}
                ]
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithNearestNeighborItem() throws InvalidProtocolBufferException {
        NearestNeighborItem nearestNeighbor = new NearestNeighborItem("myvector", "query_vector");
        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(nearestNeighbor);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemNearestNeighbor": {
                "properties": {},
                "queryTensorName": "query_vector",
                "allowApproximate": true,
                "distanceThreshold": "Infinity"
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithGeoLocationItem() throws InvalidProtocolBufferException {
        com.yahoo.prelude.Location location = new com.yahoo.prelude.Location();
        location.setAttribute("myloc");
        location.setGeoCircle(37.4, -122.1, 1000);
        GeoLocationItem geoLocation = new GeoLocationItem(location);
        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(geoLocation);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemGeoLocationTerm": {
                "properties": {"index": "myloc"},
                "hasGeoCircle": true,
                "latitude": 37.4,
                "longitude": -122.1,
                "radius": 1000.0
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithPredicateQueryItem() throws InvalidProtocolBufferException {
        PredicateQueryItem predicate = new PredicateQueryItem();
        predicate.addFeature("key", "value");
        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(predicate);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemPredicateQuery": {
                "properties": {},
                "features": [
                  {"key": "key", "value": "value", "subQueries": "18446744073709551615"}
                ]
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithUriItem() throws InvalidProtocolBufferException {
        UriItem uri = new UriItem("myindex");
        uri.addItem(new WordItem("foo"));
        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(uri);
        assertNotNull(result);
        String json = toJson(result);
        String expected = """
            {
              "itemPhrase": {
                "properties": {"index": "myindex"},
                "children": [
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "foo"}}
                ]
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithMultiRangeItem() throws InvalidProtocolBufferException {
        MultiRangeItem<Long> multiRange = MultiRangeItem.overPoints(MultiRangeItem.NumberType.LONG, "myindex",
                                                                     MultiRangeItem.Limit.INCLUSIVE,
                                                                     MultiRangeItem.Limit.INCLUSIVE);
        multiRange.addRange(1L, 10L);
        multiRange.addRange(20L, 30L);
        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(multiRange);
        assertNotNull(result);
        String json = toJson(result);
        // MultiRangeItem is converted to OR of IntItem ranges
        assertTrue(json.contains("itemOr"), "Expected itemOr in JSON: " + json);
    }

}
