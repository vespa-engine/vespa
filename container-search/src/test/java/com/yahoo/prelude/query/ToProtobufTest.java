// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.prelude.query;

import ai.vespa.json.Json;
import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ToProtobuf class
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
                   "Expected JSON: " + expectedJson);
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

}
