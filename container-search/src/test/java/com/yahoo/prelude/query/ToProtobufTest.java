// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.prelude.query;

import ai.vespa.json.TestUtils;
import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.yahoo.prelude.Location;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ToProtobuf class and toProtobuf() method in all Item subclasses
 */
public class ToProtobufTest {

    private static String toJson(SearchProtocol.QueryTreeItem item) {
        try {
            return JsonFormat.printer().print(item);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("Failed to convert protobuf to JSON", e);
        }
    }

    private static String toJson(SearchProtocol.TermItemProperties props) {
        try {
            return JsonFormat.printer().print(props);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalStateException("Failed to convert protobuf to JSON", e);
        }
    }

    private static void assertJsonEquals(String actualJson, String expectedJson) {
        assertTrue(TestUtils.equivalent(actualJson, expectedJson),
                   "Expected JSON: " + expectedJson + "\nActual JSON: " + actualJson);
    }

    private static void assertConvertsToJson(Item item, String expectedJson) {
        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(item);
        assertNotNull(result);
        assertJsonEquals(toJson(result), expectedJson);
    }

    private static void assertPropertiesAreJson(Item item, String expectedJson) {
        String index = "";
        if (item instanceof HasIndexItem indexed) {
            index = indexed.getIndexName();
        }
        SearchProtocol.TermItemProperties props = ToProtobuf.buildTermProperties(item, index);
        assertNotNull(props);
        assertJsonEquals(toJson(props), expectedJson);
    }

    @Test
    void testConvertFromQueryWithNullItem() {
        assertThrows(IllegalArgumentException.class, () -> ToProtobuf.convertFromQuery(null));
    }

    @Test
    void testConvertFromQueryWithWordItem() {
        assertConvertsToJson(new WordItem("test", "myindex"), """
                {"itemWordTerm": {"word": "test", "properties": {"index": "myindex"}}}
                """);
    }

    @Test
    void testConvertFromQueryWithAndItem() {
        AndItem and = new AndItem();
        and.addItem(new WordItem("foo", "myindex"));
        and.addItem(new WordItem("bar", "myindex"));
        assertConvertsToJson(and, """
            {
              "itemAnd": {
                "children": [
                  {"itemWordTerm": {"word": "foo", "properties": {"index": "myindex"}}},
                  {"itemWordTerm": {"word": "bar", "properties": {"index": "myindex"}}}
                ]
              }
            }
            """);
    }

    @Test
    void testBuildTermPropertiesWithDefaultValues() {
        WordItem word = new WordItem("test");
        SearchProtocol.TermItemProperties props = ToProtobuf.buildTermProperties(word, "");
        assertNotNull(props);
        assertFalse(props.hasItemWeight());
        assertFalse(props.getDoNotRank());
        assertFalse(props.getDoNotUsePositionData());
        assertFalse(props.getDoNotHighlight());
        assertFalse(props.getIsSpecialToken());
        assertJsonEquals(toJson(props), "{}");
    }

    @Test
    void testBuildTermPropertiesWithIndex() {
        WordItem word = new WordItem("test", "myindex");
        SearchProtocol.TermItemProperties props = ToProtobuf.buildTermProperties(word, word.getIndexName());
        assertEquals("myindex", props.getIndex());
        assertPropertiesAreJson(word, """
                {"index": "myindex"}
                """);
    }

    @Test
    void testBuildTermPropertiesWithWeight() {
        WordItem word = new WordItem("test", "myindex");
        word.setWeight(200);
        SearchProtocol.TermItemProperties props = ToProtobuf.buildTermProperties(word, word.getIndexName());
        assertEquals(200, props.getItemWeight());
        assertPropertiesAreJson(word, """
                {"index": "myindex", "itemWeight": 200}
                """);
    }

    @Test
    void testBuildTermPropertiesWithUniqueId() {
        WordItem word = new WordItem("test", "myindex");
        word.setUniqueID(42);
        SearchProtocol.TermItemProperties props = ToProtobuf.buildTermProperties(word, word.getIndexName());
        assertEquals(42, props.getUniqueId());
        assertPropertiesAreJson(word, """
                {"index": "myindex", "uniqueId": 42}
                """);
    }

    @Test
    void testBuildTermPropertiesWithRankedFalse() {
        WordItem word = new WordItem("test", "myindex");
        word.setRanked(false);
        SearchProtocol.TermItemProperties props = ToProtobuf.buildTermProperties(word, word.getIndexName());
        assertTrue(props.getDoNotRank());
        assertPropertiesAreJson(word, """
                {"index": "myindex", "doNotRank": true}
                """);
    }

    @Test
    void testBuildTermPropertiesWithPositionDataFalse() {
        WordItem word = new WordItem("test", "myindex");
        word.setPositionData(false);
        SearchProtocol.TermItemProperties props = ToProtobuf.buildTermProperties(word, word.getIndexName());
        assertTrue(props.getDoNotUsePositionData());
        assertPropertiesAreJson(word, """
                {"index": "myindex", "doNotUsePositionData": true}
                """);
    }

    @Test
    void testBuildTermPropertiesWithFilterTrue() {
        WordItem word = new WordItem("test", "myindex");
        word.setFilter(true);
        SearchProtocol.TermItemProperties props = ToProtobuf.buildTermProperties(word, word.getIndexName());
        assertTrue(props.getDoNotHighlight());
        assertPropertiesAreJson(word, """
                {"index": "myindex", "doNotHighlight": true}
                """);
    }

    @Test
    void testBuildTermPropertiesWithSpecialToken() {
        WordItem word = new WordItem("test", "myindex");
        word.setFromSpecialToken(true);
        SearchProtocol.TermItemProperties props = ToProtobuf.buildTermProperties(word, word.getIndexName());
        assertTrue(props.getIsSpecialToken());
        assertPropertiesAreJson(word, """
                {"index": "myindex", "isSpecialToken": true}
                """);
    }

    @Test
    void testBuildTermPropertiesWithAllProperties() {
        WordItem word = new WordItem("test", "myindex");
        word.setWeight(150);
        word.setUniqueID(99);
        word.setRanked(false);
        word.setPositionData(false);
        word.setFilter(true);
        word.setFromSpecialToken(true);

        SearchProtocol.TermItemProperties props = ToProtobuf.buildTermProperties(word, word.getIndexName());
        assertEquals("myindex", props.getIndex());
        assertEquals(150, props.getItemWeight());
        assertEquals(99, props.getUniqueId());
        assertTrue(props.getDoNotRank());
        assertTrue(props.getDoNotUsePositionData());
        assertTrue(props.getDoNotHighlight());
        assertTrue(props.getIsSpecialToken());
        assertPropertiesAreJson(word, """
            {
              "index": "myindex",
              "itemWeight": 150,
              "uniqueId": 99,
              "doNotRank": true,
              "doNotUsePositionData": true,
              "doNotHighlight": true,
              "isSpecialToken": true
            }
            """);
    }

    @Test
    void testBuildTermPropertiesWithNonIndexedItem() {
        assertPropertiesAreJson(new OrItem(), "{}");
    }

    @Test
    void testBuildTermPropertiesWithPhraseItem() {
        PhraseItem phrase = new PhraseItem("myindex");
        phrase.addItem(new WordItem("foo"));
        phrase.addItem(new WordItem("bar"));
        phrase.setWeight(250);

        SearchProtocol.TermItemProperties props = ToProtobuf.buildTermProperties(phrase, phrase.getIndexName());
        assertEquals("myindex", props.getIndex());
        assertEquals(250, props.getItemWeight());
        assertPropertiesAreJson(phrase, """
                {"index": "myindex", "itemWeight": 250}
                """);
    }

    @Test
    void testConvertFromQueryWithOrItem() {
        OrItem or = new OrItem();
        or.addItem(new WordItem("foo", "myindex"));
        or.addItem(new WordItem("bar", "myindex"));
        assertConvertsToJson(or, """
            {
              "itemOr": {
                "children": [
                  {"itemWordTerm": {"word": "foo", "properties": {"index": "myindex"}}},
                  {"itemWordTerm": {"word": "bar", "properties": {"index": "myindex"}}}
                ]
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithNestedComposite() {
        AndItem and = new AndItem();
        OrItem or = new OrItem();
        or.addItem(new WordItem("foo", "myindex"));
        or.addItem(new WordItem("bar", "myindex"));
        and.addItem(or);
        and.addItem(new WordItem("baz", "myindex"));

        assertConvertsToJson(and, """
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
            """);
    }

    @Test
    void testConvertFromQueryWithPhraseItem() {
        PhraseItem phrase = new PhraseItem("myindex");
        phrase.addItem(new WordItem("foo"));
        phrase.addItem(new WordItem("bar"));

        assertConvertsToJson(phrase, """
            {
              "itemPhrase": {
                "properties": {"index": "myindex"},
                "children": [
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "foo"}},
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "bar"}}
                ]
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithPrefixItem() {
        assertConvertsToJson(new PrefixItem("test", "myindex"), """
            {
              "itemPrefixTerm": {
                "word": "test",
                "properties": {"index": "myindex"}
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithRankItem() {
        RankItem rank = new RankItem();
        rank.addItem(new WordItem("foo", "myindex"));
        rank.addItem(new WordItem("bar", "myindex"));

        assertConvertsToJson(rank, """
            {
              "itemRank": {
                "children": [
                  {"itemWordTerm": {"word": "foo", "properties": {"index": "myindex"}}},
                  {"itemWordTerm": {"word": "bar", "properties": {"index": "myindex"}}}
                ]
              }
            }
            """);
    }

    @Test
    void testRootItemThrowsUnsupportedOperation() {
        RootItem root = new RootItem(new WordItem("test", "myindex"));
        var exception = assertThrows(UnsupportedOperationException.class,
                () -> ToProtobuf.convertFromQuery(root));
        assertTrue(exception.getMessage().contains("should not be serialized"));
    }

    @Test
    void testNullItemThrowsIllegalState() {
        var exception = assertThrows(IllegalStateException.class,
                () -> ToProtobuf.convertFromQuery(new NullItem()));
        assertTrue(exception.getMessage().contains("NullItem was attempted serialized"));
    }

    @Test
    void testPureWeightedStringThrowsUnsupportedOperation() {
        var exception = assertThrows(UnsupportedOperationException.class,
                () -> ToProtobuf.convertFromQuery(new PureWeightedString("test", 100)));
        assertTrue(exception.getMessage().contains("should not serialize itself"));
    }

    @Test
    void testPureWeightedIntegerThrowsUnsupportedOperation() {
        var exception = assertThrows(UnsupportedOperationException.class,
                () -> ToProtobuf.convertFromQuery(new PureWeightedInteger(42, 100)));
        assertTrue(exception.getMessage().contains("should not serialize itself"));
    }

    @Test
    void testConvertFromQueryWithNotItem() {
        NotItem not = new NotItem();
        not.addPositiveItem(new WordItem("foo", "myindex"));
        not.addNegativeItem(new WordItem("bar", "myindex"));

        assertConvertsToJson(not, """
            {
              "itemAndNot": {
                "children": [
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "foo"}},
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "bar"}}
                ]
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithWeakAndItem() {
        WeakAndItem weakAnd = new WeakAndItem();
        weakAnd.addItem(new WordItem("foo", "myindex"));
        weakAnd.addItem(new WordItem("bar", "myindex"));

        assertConvertsToJson(weakAnd, """
            {
              "itemWeakAnd": {
                "targetNumHits": 100,
                "children": [
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "foo"}},
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "bar"}}
                ]
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithSameElementItem() {
        SameElementItem sameElement = new SameElementItem("myfield");
        sameElement.addItem(new WordItem("foo"));
        sameElement.addItem(new WordItem("bar"));

        assertConvertsToJson(sameElement, """
            {
              "itemSameElement": {
                "properties": {"index": "myfield"},
                "children": [
                  {"itemWordTerm": {"properties": {}, "word": "foo"}},
                  {"itemWordTerm": {"properties": {}, "word": "bar"}}
                ]
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithNearItem() {
        NearItem near = new NearItem();
        near.addItem(new WordItem("foo", "myindex"));
        near.addItem(new WordItem("bar", "myindex"));

        assertConvertsToJson(near, """
            {
              "itemNear": {
                "distance": 2,
                "children": [
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "foo"}},
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "bar"}}
                ]
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithONearItem() {
        ONearItem onear = new ONearItem();
        onear.addItem(new WordItem("foo", "myindex"));
        onear.addItem(new WordItem("bar", "myindex"));

        assertConvertsToJson(onear, """
            {
              "itemOnear": {
                "distance": 2,
                "children": [
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "foo"}},
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "bar"}}
                ]
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithEquivItem() {
        EquivItem equiv = new EquivItem();
        equiv.addItem(new WordItem("foo", "myindex"));
        equiv.addItem(new WordItem("bar", "myindex"));

        assertConvertsToJson(equiv, """
            {
              "itemEquiv": {
                "properties": {},
                "children": [
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "foo"}},
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "bar"}}
                ]
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithPhraseSegmentItem() {
        PhraseSegmentItem phraseSegment = new PhraseSegmentItem("test", false, false);
        phraseSegment.addItem(new WordItem("foo"));
        phraseSegment.addItem(new WordItem("bar"));
        phraseSegment.setIndexName("myindex");

        assertConvertsToJson(phraseSegment, """
            {
              "itemPhrase": {
                "properties": {"index": "myindex"},
                "children": [
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "foo"}},
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "bar"}}
                ]
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithAndSegmentItem() {
        AndSegmentItem andSegment = new AndSegmentItem("test", false, false);
        andSegment.addItem(new WordItem("foo"));
        andSegment.addItem(new WordItem("bar"));
        andSegment.setIndexName("myindex");

        assertConvertsToJson(andSegment, """
            {
              "itemAnd": {
                "children": [
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "foo"}},
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "bar"}}
                ]
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithWeightedSetItem() {
        WeightedSetItem weightedSet = new WeightedSetItem("myindex");
        weightedSet.addToken("foo", 100);
        weightedSet.addToken("bar", 200);

        assertConvertsToJson(weightedSet, """
            {
              "itemWeightedSetOfString": {
                "properties": {
                  "index": "myindex"
                },
                "weightedStrings": [
                  {"weight": 200, "value": "bar"},
                  {"weight": 100, "value": "foo"}
                ]
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithDotProductItem() {
        DotProductItem dotProduct = new DotProductItem("myindex");
        dotProduct.addToken("foo", 100);
        dotProduct.addToken("bar", 200);

        assertConvertsToJson(dotProduct, """
            {
              "itemDotProductOfString": {
                "properties": {
                  "index": "myindex"
                },
                "weightedStrings": [
                  {"weight": 200, "value": "bar"},
                  {"weight": 100, "value": "foo"}
                ]
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithWandItem() {
        WandItem wand = new WandItem("myindex", 10);
        wand.addToken("foo", 100);
        wand.addToken("bar", 200);

        assertConvertsToJson(wand, """
            {
              "itemStringWand": {
                "properties": {
                  "index": "myindex"
                },
                "targetNumHits": 10,
                "thresholdBoostFactor": 1.0,
                "weightedStrings": [
                  {"weight": 200, "value": "bar"},
                  {"weight": 100, "value": "foo"}
                ]
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithSuffixItem() {
        SuffixItem suffix = new SuffixItem("test");
        suffix.setIndexName("myindex");
        assertConvertsToJson(suffix, """
            {
              "itemSuffixTerm": {
                "word": "test",
                "properties": {"index": "myindex"}
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithSubstringItem() {
        SubstringItem substring = new SubstringItem("test");
        substring.setIndexName("myindex");
        assertConvertsToJson(substring, """
            {
              "itemSubstringTerm": {
                "word": "test",
                "properties": {"index": "myindex"}
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithRegExpItem() {
        RegExpItem regexp = new RegExpItem("myindex", true, "test.*");
        assertConvertsToJson(regexp, """
                {"itemRegexp": {"properties": {"index": "myindex"}, "regexp": "test.*"}}
                """);
    }

    @Test
    void testConvertFromQueryWithIntItem() {
        assertConvertsToJson(new IntItem("[10;20]", "myindex"), """
            {
              "itemIntegerRangeTerm": {
                "properties": {"index": "myindex"},
                "lowerLimit": "10",
                "upperLimit": "20",
                "lowerInclusive": true,
                "upperInclusive": true
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithRangeItem() {
        assertConvertsToJson(new RangeItem(10, 20, "myindex"), """
            {
              "itemIntegerRangeTerm": {
                "properties": {"index": "myindex"},
                "lowerLimit": "10",
                "upperLimit": "20",
                "lowerInclusive": true,
                "upperInclusive": true
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithNumericInItem() {
        NumericInItem numericIn = new NumericInItem("myindex");
        numericIn.addToken(42L);
        numericIn.addToken(99L);

        assertConvertsToJson(numericIn, """
            {
              "itemNumericIn": {
                "properties": {
                  "index": "myindex"
                },
                "numbers": ["42", "99"]
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithStringInItem() {
        StringInItem stringIn = new StringInItem("myindex");
        stringIn.addToken("foo");
        stringIn.addToken("bar");

        assertConvertsToJson(stringIn, """
            {
              "itemStringIn": {
                "properties": {
                  "index": "myindex"
                },
                "words": ["bar", "foo"]
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithBoolItem() {
        assertConvertsToJson(new BoolItem(true, "myindex"), """
                {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "true"}}
                """);
    }

    @Test
    void testConvertFromQueryWithFuzzyItem() {
        assertConvertsToJson(new FuzzyItem("myindex", true, "test", 2, 0, false), """
            {
              "itemFuzzy": {
                "properties": {"index": "myindex"},
                "word": "test",
                "maxEditDistance": 2
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithExactStringItem() {
        ExactStringItem exactString = new ExactStringItem("test");
        exactString.setIndexName("myindex");
        assertConvertsToJson(exactString, """
                {"itemExactstringTerm": {"properties": {"index": "myindex"}, "word": "test"}}
                """);
    }

    @Test
    void testConvertFromQueryWithMarkerWordItem() {
        assertConvertsToJson(MarkerWordItem.createStartOfHost("myindex"), """
            {
              "itemWordTerm": {
                "word": "StArThOsT",
                "properties": {"index": "myindex"}
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithTrueItem() {
        assertConvertsToJson(new TrueItem(), """
            {
              "itemTrue": {}
            }
            """);
    }

    @Test
    void testConvertFromQueryWithFalseItem() {
        assertConvertsToJson(new FalseItem(), """
            {
              "itemFalse": {}
            }
            """);
    }

    @Test
    void testConvertFromQueryWithWordAlternativesItem() {
        WordAlternativesItem alternatives = new WordAlternativesItem("myindex", false, null,
                List.of(
                    new WordAlternativesItem.Alternative("foo", 1.0),
                    new WordAlternativesItem.Alternative("bar", 0.8)
                ));
        assertConvertsToJson(alternatives, """
            {
              "itemWordAlternatives": {
                "properties": {"index": "myindex"},
                "weightedStrings": [
                  {"weight": 80, "value": "bar"},
                  {"weight": 100, "value": "foo"}
                ]
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithWordAlternativesInsidePhrase() {
        PhraseItem phrase = new PhraseItem();
        phrase.setIndexName("myindex");
        phrase.addItem(new WordItem("hello"));
        WordAlternativesItem alternatives = new WordAlternativesItem("myindex", false, null,
                List.of(
                    new WordAlternativesItem.Alternative("world", 1.0),
                    new WordAlternativesItem.Alternative("universe", 0.7)
                ));
        phrase.addItem(alternatives);

        assertConvertsToJson(phrase, """
            {
              "itemPhrase": {
                "properties": {"index": "myindex"},
                "children": [
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "hello"}},
                  {
                    "itemWordAlternatives": {
                      "properties": {"index": "myindex"},
                      "weightedStrings": [
                        {"weight": 70, "value": "universe"},
                        {"weight": 100, "value": "world"}
                      ]
                    }
                  }
                ]
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithNearestNeighborItem() {
        assertConvertsToJson(new NearestNeighborItem("myvector", "query_vector"), """
            {
              "itemNearestNeighbor": {
                "properties": {
                  "index": "myvector"
                },
                "queryTensorName": "query_vector",
                "allowApproximate": true,
                "distanceThreshold": "Infinity"
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithNearestNeighborItemWithOptionalAttributes() {
        NearestNeighborItem nearestNeighbor = new NearestNeighborItem("myvector", "query_vector");
        nearestNeighbor.setTargetNumHits(100);
        nearestNeighbor.setAllowApproximate(false);
        nearestNeighbor.setHnswExploreAdditionalHits(50);
        nearestNeighbor.setDistanceThreshold(0.5);
        nearestNeighbor.setWeight(200);
        nearestNeighbor.setFilter(true);

        assertConvertsToJson(nearestNeighbor, """
            {
              "itemNearestNeighbor": {
                "properties": {
                  "index": "myvector",
                  "itemWeight": 200,
                  "doNotHighlight": true
                },
                "queryTensorName": "query_vector",
                "targetNumHits": 100,
                "exploreAdditionalHits": 50,
                "distanceThreshold": 0.5
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithGeoLocationItem() {
        Location location = new Location();
        location.setAttribute("myloc");
        location.setGeoCircle(37.4, -122.1, 1000);
        GeoLocationItem geoLocation = new GeoLocationItem(location);
        assertConvertsToJson(geoLocation, """
            {
              "itemGeoLocationTerm": {
                "properties": {"index": "myloc"},
                "hasGeoCircle": true,
                "latitude": 37.4,
                "longitude": -122.1,
                "radius": 1000.0
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithPredicateQueryItem() {
        PredicateQueryItem predicate = new PredicateQueryItem();
        predicate.setIndexName("myindex");
        predicate.addFeature("key", "value");
        assertConvertsToJson(predicate, """
            {
              "itemPredicateQuery": {
                "properties": {
                  "index": "myindex"
                },
                "features": [
                  {"key": "key", "value": "value", "subQueries": "18446744073709551615"}
                ]
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithUriItem() {
        UriItem uri = new UriItem("myindex");
        uri.addItem(new WordItem("foo"));
        assertConvertsToJson(uri, """
            {
              "itemPhrase": {
                "properties": {"index": "myindex"},
                "children": [
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "foo"}}
                ]
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithMultiRangeItem() {
        MultiRangeItem<Long> multiRange = MultiRangeItem.overPoints(MultiRangeItem.NumberType.LONG, "myindex",
                                                                    MultiRangeItem.Limit.INCLUSIVE,
                                                                    MultiRangeItem.Limit.INCLUSIVE);
        multiRange.addRange(1L, 10L);
        multiRange.addRange(20L, 30L);
        SearchProtocol.QueryTreeItem result = ToProtobuf.convertFromQuery(multiRange);
        assertNotNull(result);
        String json = toJson(result);
        // MultiRangeItem is converted to OR of IntItem ranges
        String expected = """
            {
              "itemOr": {
                "children": [
                  {
                    "itemIntegerRangeTerm": {
                      "properties": {"index": "myindex"},
                      "lowerLimit": "1",
                      "upperLimit": "10",
                      "lowerInclusive": true,
                      "upperInclusive": true
                    }
                  },
                  {
                    "itemIntegerRangeTerm": {
                      "properties": {"index": "myindex"},
                      "lowerLimit": "20",
                      "upperLimit": "30",
                      "lowerInclusive": true,
                      "upperInclusive": true
                    }
                  }
                ]
              }
            }
            """;
        assertJsonEquals(json, expected);
    }

    @Test
    void testConvertFromQueryWithOptionalAttributes() {
        WordItem word = new WordItem("test", "myindex");
        word.setWeight(200);
        word.setUniqueID(42);
        word.setRanked(false);
        word.setPositionData(false);
        word.setFilter(true);

        assertConvertsToJson(word, """
            {
              "itemWordTerm": {
                "word": "test",
                "properties": {
                  "index": "myindex",
                  "itemWeight": 200,
                  "uniqueId": 42,
                  "doNotRank": true,
                  "doNotUsePositionData": true,
                  "doNotHighlight": true
                }
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithWeightOnly() {
        WordItem word = new WordItem("test", "myindex");
        word.setWeight(150);

        assertConvertsToJson(word, """
            {
              "itemWordTerm": {
                "word": "test",
                "properties": {
                  "index": "myindex",
                  "itemWeight": 150
                }
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithUniqueIdOnly() {
        WordItem word = new WordItem("test", "myindex");
        word.setUniqueID(123);

        assertConvertsToJson(word, """
            {
              "itemWordTerm": {
                "word": "test",
                "properties": {
                  "index": "myindex",
                  "uniqueId": 123
                }
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithPhraseItemAndWeight() {
        PhraseItem phrase = new PhraseItem();
        phrase.setIndexName("myindex");
        phrase.addItem(new WordItem("foo"));
        phrase.addItem(new WordItem("bar"));
        phrase.setWeight(250);

        assertConvertsToJson(phrase, """
            {
              "itemPhrase": {
                "properties": {
                  "index": "myindex",
                  "itemWeight": 250
                },
                "children": [
                  {"itemWordTerm": {"properties": {"index": "myindex", "itemWeight": 250}, "word": "foo"}},
                  {"itemWordTerm": {"properties": {"index": "myindex", "itemWeight": 250}, "word": "bar"}}
                ]
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithEquivItemAndUniqueId() {
        EquivItem equiv = new EquivItem();
        equiv.addItem(new WordItem("foo", "myindex"));
        equiv.addItem(new WordItem("bar", "myindex"));
        equiv.setUniqueID(999);

        assertConvertsToJson(equiv, """
            {
              "itemEquiv": {
                "properties": {
                  "uniqueId": 999
                },
                "children": [
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "foo"}},
                  {"itemWordTerm": {"properties": {"index": "myindex"}, "word": "bar"}}
                ]
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithRankedFalse() {
        WordItem word = new WordItem("test", "myindex");
        word.setRanked(false);

        assertConvertsToJson(word, """
            {
              "itemWordTerm": {
                "word": "test",
                "properties": {
                  "index": "myindex",
                  "doNotRank": true
                }
              }
            }
            """);
    }

    @Test
    void testConvertFromQueryWithFilterTrue() {
        WordItem word = new WordItem("test", "myindex");
        word.setFilter(true);

        assertConvertsToJson(word, """
            {
              "itemWordTerm": {
                "word": "test",
                "properties": {
                  "index": "myindex",
                  "doNotHighlight": true
                }
              }
            }
            """);
    }

}
