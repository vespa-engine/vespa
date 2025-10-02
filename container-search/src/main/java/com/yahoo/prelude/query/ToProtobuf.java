// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;

public class ToProtobuf {

    /*
     * Convert any Item to SearchProtocol.QueryTreeItem
     */

    static SearchProtocol.QueryTreeItem convertFromQuery(Item item) {
        if (item == null) {
            throw new IllegalArgumentException("Cannot convert null item");
        }

        var builder = SearchProtocol.QueryTreeItem.newBuilder();

        switch (item.getItemType()) {
            case TRUE -> builder.setItemTrue(SearchProtocol.ItemTrue.newBuilder().build());
            case FALSE -> builder.setItemFalse(SearchProtocol.ItemFalse.newBuilder().build());

            // Simple composites
            case OR -> builder.setItemOr(convertOr((OrItem) item));
            case AND -> builder.setItemAnd(convertAnd((AndItem) item));
            case NOT -> builder.setItemAndNot(convertAndNot((NotItem) item));
            case RANK -> builder.setItemRank(convertRank((RankItem) item));
            case NEAR -> builder.setItemNear(convertNear((NearItem) item));
            case ONEAR -> builder.setItemOnear(convertOnear((ONearItem) item));
            case WEAK_AND -> builder.setItemWeakAnd(convertWeakAnd((WeakAndItem) item));

            // Pure weighted
            case PURE_WEIGHTED_STRING -> builder.setItemPureWeightedString(convertPureWeightedString((PureWeightedString) item));
            case PURE_WEIGHTED_INTEGER -> builder.setItemPureWeightedLong(convertPureWeightedInteger((PureWeightedInteger) item));

            // Simple term items
            case WORD -> builder.setItemWordTerm(convertWord((WordItem) item));
            case INT -> { return convertIntItem((IntItem) item); }
            case EXACT -> builder.setItemExactstringTerm(convertExact((ExactStringItem) item));
            case PREFIX -> builder.setItemPrefixTerm(convertPrefix((PrefixItem) item));
            case SUFFIX -> builder.setItemSuffixTerm(convertSuffix((SuffixItem) item));
            case SUBSTRING -> builder.setItemSubstringTerm(convertSubstring((SubstringItem) item));
            case REGEXP -> builder.setItemRegexp(convertRegexp((RegExpItem) item));
            case FUZZY -> builder.setItemFuzzy(convertFuzzy((FuzzyItem) item));

            // Multi-terms
            case WEIGHTEDSET -> { return convertWeightedSet((WeightedSetItem) item); }
            case DOTPRODUCT -> { return convertDotProduct((DotProductItem) item); }
            case WAND -> { return convertWand((WandItem) item); }
            case EQUIV -> builder.setItemEquiv(convertEquiv((EquivItem) item));
            case WORD_ALTERNATIVES -> builder.setItemWordAlternatives(convertWordAlternatives((WordAlternativesItem) item));
            case STRING_IN -> builder.setItemStringIn(convertStringIn((StringInItem) item));
            case NUMERIC_IN -> builder.setItemNumericIn(convertNumericIn((NumericInItem) item));

            // Phrase and SameElement
            case PHRASE -> builder.setItemPhrase(convertPhrase((PhraseItem) item));
            case SAME_ELEMENT -> builder.setItemSameElement(convertSameElement((SameElementItem) item));

            // Special items
            case NEAREST_NEIGHBOR -> builder.setItemNearestNeighbor(convertNearestNeighbor((NearestNeighborItem) item));
            case GEO_LOCATION_TERM -> builder.setItemGeoLocationTerm(convertGeoLocation((GeoLocationItem) item));
            case PREDICATE_QUERY -> builder.setItemPredicateQuery(convertPredicateQuery((PredicateQueryItem) item));

            default -> throw new IllegalArgumentException("Unsupported item type: " + item.getItemType());
        }

        return builder.build();
    }

    private static SearchProtocol.TermItemProperties buildTermProperties(Item item) {
        var props = SearchProtocol.TermItemProperties.newBuilder();

        if (item instanceof IndexedItem indexedItem) {
            props.setIndex(indexedItem.getIndexName());
        }

        if (item.getWeight() != Item.DEFAULT_WEIGHT) {
            props.setItemWeight(item.getWeight());
        }

        if (item.hasUniqueID()) {
            props.setUniqueId(item.uniqueID);
        }

        if (!item.isRanked()) {
            props.setDoNotRank(true);
        }

        if (!item.usePositionData()) {
            props.setDoNotUsePositionData(true);
        }

        if (item.isFilter()) {
            props.setDoNotHighlight(true);
        }

        if (item.isFromSpecialToken()) {
            props.setIsSpecialToken(true);
        }

        return props.build();
    }

    // Simple composites
    private static SearchProtocol.ItemOr convertOr(OrItem item) {
        var builder = SearchProtocol.ItemOr.newBuilder();
        for (int i = 0; i < item.getItemCount(); i++) {
            builder.addChildren(convertFromQuery(item.getItem(i)));
        }
        return builder.build();
    }

    private static SearchProtocol.ItemAnd convertAnd(AndItem item) {
        var builder = SearchProtocol.ItemAnd.newBuilder();
        for (int i = 0; i < item.getItemCount(); i++) {
            builder.addChildren(convertFromQuery(item.getItem(i)));
        }
        return builder.build();
    }

    private static SearchProtocol.ItemAndNot convertAndNot(NotItem item) {
        var builder = SearchProtocol.ItemAndNot.newBuilder();
        for (int i = 0; i < item.getItemCount(); i++) {
            builder.addChildren(convertFromQuery(item.getItem(i)));
        }
        return builder.build();
    }

    private static SearchProtocol.ItemRank convertRank(RankItem item) {
        var builder = SearchProtocol.ItemRank.newBuilder();
        for (int i = 0; i < item.getItemCount(); i++) {
            builder.addChildren(convertFromQuery(item.getItem(i)));
        }
        return builder.build();
    }

    private static SearchProtocol.ItemNear convertNear(NearItem item) {
        var builder = SearchProtocol.ItemNear.newBuilder();
        builder.setDistance(item.getDistance());
        for (int i = 0; i < item.getItemCount(); i++) {
            builder.addChildren(convertFromQuery(item.getItem(i)));
        }
        return builder.build();
    }

    private static SearchProtocol.ItemOnear convertOnear(ONearItem item) {
        var builder = SearchProtocol.ItemOnear.newBuilder();
        builder.setDistance(item.getDistance());
        for (int i = 0; i < item.getItemCount(); i++) {
            builder.addChildren(convertFromQuery(item.getItem(i)));
        }
        return builder.build();
    }

    private static SearchProtocol.ItemWeakAnd convertWeakAnd(WeakAndItem item) {
        var builder = SearchProtocol.ItemWeakAnd.newBuilder();
        builder.setIndex(item.getIndexName());
        builder.setTargetNumHits(item.getN());
        for (int i = 0; i < item.getItemCount(); i++) {
            builder.addChildren(convertFromQuery(item.getItem(i)));
        }
        return builder.build();
    }

    // Pure weighted
    private static SearchProtocol.ItemPureWeightedString convertPureWeightedString(PureWeightedString item) {
        return SearchProtocol.ItemPureWeightedString.newBuilder()
                .setWeight(item.getWeight())
                .setValue(item.getString())
                .build();
    }

    private static SearchProtocol.ItemPureWeightedLong convertPureWeightedInteger(PureWeightedInteger item) {
        return SearchProtocol.ItemPureWeightedLong.newBuilder()
                .setWeight(item.getWeight())
                .setValue(item.getValue())
                .build();
    }

    // Simple term items
    private static SearchProtocol.ItemWordTerm convertWord(WordItem item) {
        return SearchProtocol.ItemWordTerm.newBuilder()
                .setProperties(buildTermProperties(item))
                .setWord(item.getWord())
                .build();
    }

    private static SearchProtocol.QueryTreeItem convertIntItem(IntItem item) {
        var builder = SearchProtocol.QueryTreeItem.newBuilder();

        // Check if this is a range
        Limit from = item.getFromLimit();
        Limit to = item.getToLimit();

        // If both limits are the same and both are numbers (not infinity), it's a simple int/float term
        if (from.equals(to) && !from.isInfinite()) {
            Number num = from.number();
            if (num instanceof Double || num instanceof Float) {
                builder.setItemFloatingPointTerm(SearchProtocol.ItemFloatingPointTerm.newBuilder()
                        .setProperties(buildTermProperties(item))
                        .setNumber(num.doubleValue())
                        .build());
            } else {
                builder.setItemIntegerTerm(SearchProtocol.ItemIntegerTerm.newBuilder()
                        .setProperties(buildTermProperties(item))
                        .setNumber(num.longValue())
                        .build());
            }
        } else {
            // It's a range
            Number fromNum = from.number();
            Number toNum = to.number();

            // Determine if we should use integer or floating point range
            boolean isFloatingPoint = (fromNum instanceof Double || fromNum instanceof Float ||
                                      toNum instanceof Double || toNum instanceof Float);

            if (isFloatingPoint) {
                var rangeBuilder = SearchProtocol.ItemFloatingPointRangeTerm.newBuilder()
                        .setProperties(buildTermProperties(item))
                        .setLowerLimit(fromNum.doubleValue())
                        .setUpperLimit(toNum.doubleValue())
                        .setLowerInclusive(from.isInclusive())
                        .setUpperInclusive(to.isInclusive());

                if (item.getHitLimit() != 0) {
                    rangeBuilder.setHasRangeLimit(true);
                    rangeBuilder.setRangeLimit(item.getHitLimit());
                }

                builder.setItemFloatingPointRangeTerm(rangeBuilder.build());
            } else {
                var rangeBuilder = SearchProtocol.ItemIntegerRangeTerm.newBuilder()
                        .setProperties(buildTermProperties(item))
                        .setLowerLimit(fromNum.longValue())
                        .setUpperLimit(toNum.longValue());

                if (item.getHitLimit() != 0) {
                    rangeBuilder.setHasRangeLimit(true);
                    rangeBuilder.setRangeLimit(item.getHitLimit());
                }

                builder.setItemIntegerRangeTerm(rangeBuilder.build());
            }
        }

        return builder.build();
    }

    private static SearchProtocol.ItemExactStringTerm convertExact(ExactStringItem item) {
        return SearchProtocol.ItemExactStringTerm.newBuilder()
                .setProperties(buildTermProperties(item))
                .setWord(item.getWord())
                .build();
    }

    private static SearchProtocol.ItemPrefixTerm convertPrefix(PrefixItem item) {
        return SearchProtocol.ItemPrefixTerm.newBuilder()
                .setProperties(buildTermProperties(item))
                .setWord(item.getWord())
                .build();
    }

    private static SearchProtocol.ItemSuffixTerm convertSuffix(SuffixItem item) {
        return SearchProtocol.ItemSuffixTerm.newBuilder()
                .setProperties(buildTermProperties(item))
                .setWord(item.getWord())
                .build();
    }

    private static SearchProtocol.ItemSubstringTerm convertSubstring(SubstringItem item) {
        return SearchProtocol.ItemSubstringTerm.newBuilder()
                .setProperties(buildTermProperties(item))
                .setWord(item.getWord())
                .build();
    }

    private static SearchProtocol.ItemRegexp convertRegexp(RegExpItem item) {
        return SearchProtocol.ItemRegexp.newBuilder()
                .setProperties(buildTermProperties(item))
                .setRegexp(item.stringValue())
                .build();
    }

    private static SearchProtocol.ItemFuzzy convertFuzzy(FuzzyItem item) {
        return SearchProtocol.ItemFuzzy.newBuilder()
                .setProperties(buildTermProperties(item))
                .setWord(item.stringValue())
                .setMaxEditDistance(item.getMaxEditDistance())
                .setPrefixLockLength(item.getPrefixLength())
                .setPrefixMatch(item.isPrefixMatch())
                .build();
    }

    // Multi-terms
    private static SearchProtocol.QueryTreeItem convertWeightedSet(WeightedSetItem item) {
        var builder = SearchProtocol.QueryTreeItem.newBuilder();

        // Check if tokens are strings or longs
        var tokens = item.getTokens();
        boolean hasStrings = false;
        boolean hasLongs = false;

        while (tokens.hasNext()) {
            var entry = tokens.next();
            if (entry.getKey() instanceof String) {
                hasStrings = true;
            } else if (entry.getKey() instanceof Long || entry.getKey() instanceof Integer) {
                hasLongs = true;
            }
        }

        // Reset iterator
        tokens = item.getTokens();

        if (hasStrings) {
            var setBuilder = SearchProtocol.ItemWeightedSetOfString.newBuilder();
            setBuilder.setProperties(buildTermProperties(item));
            while (tokens.hasNext()) {
                var entry = tokens.next();
                if (entry.getKey() instanceof String) {
                    setBuilder.addWeightedStrings(SearchProtocol.ItemPureWeightedString.newBuilder()
                            .setWeight(entry.getValue())
                            .setValue((String) entry.getKey())
                            .build());
                }
            }
            builder.setItemWeightedSetOfString(setBuilder.build());
        } else if (hasLongs) {
            var setBuilder = SearchProtocol.ItemWeightedSetOfLong.newBuilder();
            setBuilder.setProperties(buildTermProperties(item));
            while (tokens.hasNext()) {
                var entry = tokens.next();
                long value = entry.getKey() instanceof Long ? (Long) entry.getKey() : ((Integer) entry.getKey()).longValue();
                setBuilder.addWeightedLongs(SearchProtocol.ItemPureWeightedLong.newBuilder()
                        .setWeight(entry.getValue())
                        .setValue(value)
                        .build());
            }
            builder.setItemWeightedSetOfLong(setBuilder.build());
        }

        return builder.build();
    }

    private static SearchProtocol.QueryTreeItem convertDotProduct(DotProductItem item) {
        var builder = SearchProtocol.QueryTreeItem.newBuilder();

        // Check if tokens are strings or longs
        var tokens = item.getTokens();
        boolean hasStrings = false;
        boolean hasLongs = false;

        while (tokens.hasNext()) {
            var entry = tokens.next();
            if (entry.getKey() instanceof String) {
                hasStrings = true;
            } else if (entry.getKey() instanceof Long || entry.getKey() instanceof Integer) {
                hasLongs = true;
            }
        }

        // Reset iterator
        tokens = item.getTokens();

        if (hasStrings) {
            var dpBuilder = SearchProtocol.ItemDotProductOfString.newBuilder();
            dpBuilder.setProperties(buildTermProperties(item));
            while (tokens.hasNext()) {
                var entry = tokens.next();
                if (entry.getKey() instanceof String) {
                    dpBuilder.addWeightedStrings(SearchProtocol.ItemPureWeightedString.newBuilder()
                            .setWeight(entry.getValue())
                            .setValue((String) entry.getKey())
                            .build());
                }
            }
            builder.setItemDotProductOfString(dpBuilder.build());
        } else if (hasLongs) {
            var dpBuilder = SearchProtocol.ItemDotProductOfLong.newBuilder();
            dpBuilder.setProperties(buildTermProperties(item));
            while (tokens.hasNext()) {
                var entry = tokens.next();
                long value = entry.getKey() instanceof Long ? (Long) entry.getKey() : ((Integer) entry.getKey()).longValue();
                dpBuilder.addWeightedLongs(SearchProtocol.ItemPureWeightedLong.newBuilder()
                        .setWeight(entry.getValue())
                        .setValue(value)
                        .build());
            }
            builder.setItemDotProductOfLong(dpBuilder.build());
        }

        return builder.build();
    }

    private static SearchProtocol.QueryTreeItem convertWand(WandItem item) {
        var builder = SearchProtocol.QueryTreeItem.newBuilder();

        // Check if tokens are strings or longs
        var tokens = item.getTokens();
        boolean hasStrings = false;
        boolean hasLongs = false;

        while (tokens.hasNext()) {
            var entry = tokens.next();
            if (entry.getKey() instanceof String) {
                hasStrings = true;
            } else if (entry.getKey() instanceof Long || entry.getKey() instanceof Integer) {
                hasLongs = true;
            }
        }

        // Reset iterator
        tokens = item.getTokens();

        if (hasStrings) {
            var wandBuilder = SearchProtocol.ItemStringWand.newBuilder();
            wandBuilder.setProperties(buildTermProperties(item));
            wandBuilder.setTargetNumHits(item.getTargetNumHits());
            wandBuilder.setScoreThreshold(item.getScoreThreshold());
            wandBuilder.setThresholdBoostFactor(item.getThresholdBoostFactor());
            while (tokens.hasNext()) {
                var entry = tokens.next();
                if (entry.getKey() instanceof String) {
                    wandBuilder.addWeightedStrings(SearchProtocol.ItemPureWeightedString.newBuilder()
                            .setWeight(entry.getValue())
                            .setValue((String) entry.getKey())
                            .build());
                }
            }
            builder.setItemStringWand(wandBuilder.build());
        } else if (hasLongs) {
            var wandBuilder = SearchProtocol.ItemLongWand.newBuilder();
            wandBuilder.setProperties(buildTermProperties(item));
            wandBuilder.setTargetNumHits(item.getTargetNumHits());
            wandBuilder.setScoreThreshold(item.getScoreThreshold());
            wandBuilder.setThresholdBoostFactor(item.getThresholdBoostFactor());
            while (tokens.hasNext()) {
                var entry = tokens.next();
                long value = entry.getKey() instanceof Long ? (Long) entry.getKey() : ((Integer) entry.getKey()).longValue();
                wandBuilder.addWeightedLongs(SearchProtocol.ItemPureWeightedLong.newBuilder()
                        .setWeight(entry.getValue())
                        .setValue(value)
                        .build());
            }
            builder.setItemLongWand(wandBuilder.build());
        }

        return builder.build();
    }

    private static SearchProtocol.ItemEquiv convertEquiv(EquivItem item) {
        var builder = SearchProtocol.ItemEquiv.newBuilder();
        builder.setProperties(buildTermProperties(item));
        for (int i = 0; i < item.getItemCount(); i++) {
            builder.addChildren(convertFromQuery(item.getItem(i)));
        }
        return builder.build();
    }

    private static SearchProtocol.ItemWordAlternatives convertWordAlternatives(WordAlternativesItem item) {
        var builder = SearchProtocol.ItemWordAlternatives.newBuilder();
        builder.setProperties(buildTermProperties(item));
        for (var alt : item.getAlternatives()) {
            builder.addWeightedStrings(SearchProtocol.ItemPureWeightedString.newBuilder()
                    .setWeight((int) (item.getWeight() * alt.exactness + 0.5))
                    .setValue(alt.word)
                    .build());
        }
        return builder.build();
    }

    private static SearchProtocol.ItemStringIn convertStringIn(StringInItem item) {
        var builder = SearchProtocol.ItemStringIn.newBuilder();
        builder.setProperties(buildTermProperties(item));
        for (String token : item.getTokens()) {
            builder.addWords(token);
        }
        return builder.build();
    }

    private static SearchProtocol.ItemNumericIn convertNumericIn(NumericInItem item) {
        var builder = SearchProtocol.ItemNumericIn.newBuilder();
        builder.setProperties(buildTermProperties(item));
        for (Long token : item.getTokens()) {
            builder.addNumbers(token);
        }
        return builder.build();
    }

    // Phrase and SameElement
    private static SearchProtocol.ItemPhrase convertPhrase(PhraseItem item) {
        var builder = SearchProtocol.ItemPhrase.newBuilder();
        builder.setProperties(buildTermProperties(item));
        for (int i = 0; i < item.getItemCount(); i++) {
            builder.addChildren(convertFromQuery(item.getItem(i)));
        }
        return builder.build();
    }

    private static SearchProtocol.ItemSameElement convertSameElement(SameElementItem item) {
        var builder = SearchProtocol.ItemSameElement.newBuilder();
        var props = SearchProtocol.TermItemProperties.newBuilder();
        props.setIndex(item.getFieldName());
        builder.setProperties(props);
        for (int i = 0; i < item.getItemCount(); i++) {
            builder.addChildren(convertFromQuery(item.getItem(i)));
        }
        return builder.build();
    }

    // Special items
    private static SearchProtocol.ItemNearestNeighbor convertNearestNeighbor(NearestNeighborItem item) {
        return SearchProtocol.ItemNearestNeighbor.newBuilder()
                .setProperties(buildTermProperties(item))
                .setQueryTensorName(item.getQueryTensorName())
                .setTargetNumHits(item.getTargetNumHits())
                .setAllowApproximate(item.getAllowApproximate())
                .setExploreAdditionalHits(item.getHnswExploreAdditionalHits())
                .setDistanceThreshold(item.getDistanceThreshold())
                .build();
    }

    private static SearchProtocol.ItemGeoLocationTerm convertGeoLocation(GeoLocationItem item) {
        var builder = SearchProtocol.ItemGeoLocationTerm.newBuilder();
        builder.setProperties(buildTermProperties(item));

        var location = item.getLocation();

        if (location.isGeoCircle()) {
            builder.setHasGeoCircle(true);
            builder.setLatitude(location.degNS());
            builder.setLongitude(location.degEW());
            builder.setRadius(location.degRadius());
        }

        if (location.hasBoundingBox()) {
            builder.setHasBoundingBox(true);
            // The Location class stores bounding box as x1, y1, x2, y2 in microdegrees
            // We need to convert to degrees for the protobuf
            String[] parts = location.bbInDegrees().split(", ");
            builder.setS(Double.parseDouble(parts[0]));  // south (y1)
            builder.setW(Double.parseDouble(parts[1]));  // west (x1)
            builder.setN(Double.parseDouble(parts[2]));  // north (y2)
            builder.setE(Double.parseDouble(parts[3]));  // east (x2)
        }

        return builder.build();
    }

    private static SearchProtocol.ItemPredicateQuery convertPredicateQuery(PredicateQueryItem item) {
        var builder = SearchProtocol.ItemPredicateQuery.newBuilder();
        builder.setProperties(buildTermProperties(item));

        for (var feature : item.getFeatures()) {
            builder.addFeatures(SearchProtocol.PredicateFeature.newBuilder()
                    .setKey(feature.getKey())
                    .setValue(feature.getValue())
                    .setSubQueries(feature.getSubQueryBitmap())
                    .build());
        }

        for (var rangeFeature : item.getRangeFeatures()) {
            builder.addRangeFeatures(SearchProtocol.PredicateRangeFeature.newBuilder()
                    .setKey(rangeFeature.getKey())
                    .setValue(rangeFeature.getValue())
                    .setSubQueries(rangeFeature.getSubQueryBitmap())
                    .build());
        }

        return builder.build();
    }

}
