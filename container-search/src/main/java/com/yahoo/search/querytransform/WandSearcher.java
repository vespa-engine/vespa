// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.*;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.text.MapParser;

import java.util.LinkedHashMap;
import java.util.Map;

import com.yahoo.yolean.Exceptions;

/**
 * Searcher that will create a Vespa WAND item from a list of tokens with weights.
 * IndexFacts is used to determine which WAND to create.
 *
 * @author geirst
 * @author bratseth
 */
public class WandSearcher extends Searcher {

    /**
     * Enum used to represent which "wand" this searcher should produce.
     */
    private enum WandType {
        VESPA("vespa"),
        OR("or"),
        PARALLEL("parallel"),
        DOT_PRODUCT("dotProduct");

        private final String type;

        WandType(String type) {
            this.type = type;
        }

        public static WandType create(String type) {
            for (WandType enumType : WandType.values()) {
                if (enumType.type.equals(type)) {
                    return enumType;
                }
            }
            return WandType.VESPA;
        }
    }

    /**
     * Class to resolve the inputs used by this searcher.
     */
    private static class InputResolver {

        private static final CompoundName WAND_FIELD = new CompoundName("wand.field");
        private static final CompoundName WAND_TOKENS = new CompoundName("wand.tokens");
        private static final CompoundName WAND_HEAP_SIZE = new CompoundName("wand.heapSize");
        private static final CompoundName WAND_TYPE = new CompoundName("wand.type");
        private static final CompoundName WAND_SCORE_THRESHOLD = new CompoundName("wand.scoreThreshold");
        private static final CompoundName WAND_THRESHOLD_BOOST_FACTOR = new CompoundName("wand.thresholdBoostFactor");
        private final String fieldName;
        private final WandType wandType;
        private final Map<String, Integer> tokens;
        private final int heapSize;
        private final double scoreThreshold;
        private final double thresholdBoostFactor;

        public InputResolver(Query query, Execution execution) {
            fieldName = query.properties().getString(WAND_FIELD);
            if (fieldName != null) {
                String tokens = query.properties().getString(WAND_TOKENS);
                if (tokens != null) {
                    wandType = resolveWandType(execution.context().getIndexFacts().newSession(query), query);
                    this.tokens = new IntegerMapParser().parse(tokens, new LinkedHashMap<>());
                    heapSize = resolveHeapSize(query);
                    scoreThreshold = resolveScoreThreshold(query);
                    thresholdBoostFactor = resolveThresholdBoostFactor(query);
                    return;
                }
            }
            wandType = null;
            tokens = null;
            heapSize = 0;
            scoreThreshold = 0;
            thresholdBoostFactor = 1;
        }

        private WandType resolveWandType(IndexFacts.Session indexFacts, Query query) {
            Index index = indexFacts.getIndex(fieldName);
            if (index.isNull()) {
                throw new IllegalArgumentException("Field '" + fieldName + "' was not found in " + indexFacts);
            } else {
                return WandType.create(query.properties().getString(WAND_TYPE, "vespa"));
            }
        }

        private int resolveHeapSize(Query query) {
            String defaultHeapSize = "100";
            return Integer.valueOf(query.properties().getString(WAND_HEAP_SIZE, defaultHeapSize));
        }

        private double resolveScoreThreshold(Query query) {
            return Double.valueOf(query.properties().getString(WAND_SCORE_THRESHOLD, "0"));
        }

        private double resolveThresholdBoostFactor(Query query) {
            return Double.valueOf(query.properties().getString(WAND_THRESHOLD_BOOST_FACTOR, "1"));
        }

        public boolean hasValidData() {
            return tokens != null && !tokens.isEmpty();
        }

        public String getFieldName() {
            return fieldName;
        }

        public Map<String, Integer> getTokens() {
            return tokens;
        }

        public WandType getWandType() {
            return wandType;
        }

        public Integer getHeapSize() {
            return heapSize;
        }

        public Double getScoreThreshold() {
            return scoreThreshold;
        }

        public Double getThresholdBoostFactor() {
            return thresholdBoostFactor;
        }
    }

    @Override
    public Result search(Query query, Execution execution) {
        try {
            InputResolver inputs = new InputResolver(query, execution);
            if ( ! inputs.hasValidData()) return execution.search(query);

            query.getModel().getQueryTree().and(createWandQueryItem(inputs));
            query.trace("WandSearcher: Added WAND operator", true, 4);
            return execution.search(query);
        }
        catch (IllegalArgumentException e) {
            return new Result(query,ErrorMessage.createInvalidQueryParameter(Exceptions.toMessageString(e)));
        }
    }

    private Item createWandQueryItem(InputResolver inputs) {
        if (inputs.getWandType().equals(WandType.VESPA)) {
            return populate(new WeakAndItem(inputs.getHeapSize()), inputs.getFieldName(), inputs.getTokens());
        } else if (inputs.getWandType().equals(WandType.OR)) {
            return populate(new OrItem(), inputs.getFieldName(), inputs.getTokens());
        } else if (inputs.getWandType().equals(WandType.PARALLEL)) {
            return populate(new WandItem(inputs.getFieldName(), inputs.getHeapSize()),
                    inputs.getScoreThreshold(), inputs.getThresholdBoostFactor(), inputs.getTokens());
        } else if (inputs.getWandType().equals(WandType.DOT_PRODUCT)) {
            return populate(new DotProductItem(inputs.getFieldName()), inputs.getTokens());
        }
        throw new IllegalArgumentException("Unknown type '" + inputs.getWandType() + "'");
    }

    private CompositeItem populate(CompositeItem parent, String fieldName, Map<String,Integer> tokens) {
        for (Map.Entry<String,Integer> entry : tokens.entrySet()) {
            WordItem wordItem = new WordItem(entry.getKey(), fieldName);
            wordItem.setWeight(entry.getValue());
            wordItem.setStemmed(true);
            wordItem.setNormalizable(false);
            parent.addItem(wordItem);
        }
        return parent;
    }

    private WeightedSetItem populate(WeightedSetItem item, Map<String,Integer> tokens) {
        for (Map.Entry<String,Integer> entry : tokens.entrySet()) {
            item.addToken(entry.getKey(), entry.getValue());
        }
        return item;
    }

    private WandItem populate(WandItem item, double scoreThreshold, double thresholdBoostFactor, Map<String,Integer> tokens) {
        populate(item, tokens);
        item.setScoreThreshold(scoreThreshold);
        item.setThresholdBoostFactor(thresholdBoostFactor);
        return item;
    }

    private static class IntegerMapParser extends MapParser<Integer> {
        @Override
        protected Integer parseValue(String s) {
            return Integer.parseInt(s);
        }
    }

}
