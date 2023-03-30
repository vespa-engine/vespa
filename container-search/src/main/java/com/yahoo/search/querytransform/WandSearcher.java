// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import com.yahoo.prelude.Index;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.query.CompositeItem;
import com.yahoo.prelude.query.DotProductItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.WandItem;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;

import java.util.LinkedHashMap;
import java.util.Map;

import com.yahoo.text.SimpleMapParser;
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

        private static final CompoundName WAND_FIELD = CompoundName.from("wand.field");
        private static final CompoundName WAND_TOKENS = CompoundName.from("wand.tokens");
        private static final CompoundName WAND_HEAP_SIZE = CompoundName.from("wand.heapSize");
        private static final CompoundName WAND_TYPE = CompoundName.from("wand.type");
        private static final CompoundName WAND_SCORE_THRESHOLD = CompoundName.from("wand.scoreThreshold");
        private static final CompoundName WAND_THRESHOLD_BOOST_FACTOR = CompoundName.from("wand.thresholdBoostFactor");
        private final String fieldName;
        private final WandType wandType;
        private final Map<Object, Integer> tokens;
        private final int heapSize;
        private final double scoreThreshold;
        private final double thresholdBoostFactor;

        public InputResolver(Query query, Execution execution) {
            fieldName = query.properties().getString(WAND_FIELD);
            if (fieldName != null) {
                String tokens = query.properties().getString(WAND_TOKENS);
                if (tokens != null) {
                    IndexFacts.Session indexFacts = execution.context().getIndexFacts().newSession(query);
                    Index index = indexFacts.getIndex(fieldName);
                    wandType = resolveWandType(index, indexFacts, query);
                    if (index.isNumerical() && (wandType == WandType.DOT_PRODUCT || wandType == WandType.PARALLEL)) {
                        this.tokens = new LongIntegerMapParser().parse(tokens, new LinkedHashMap<>(200));
                    } else {
                        this.tokens = new MapObjectIntegerParser().parse(tokens, new LinkedHashMap<>(200));
                    }
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

        private WandType resolveWandType(Index index, IndexFacts.Session indexFacts, Query query) {
            if (index.isNull()) {
                throw new IllegalInputException("Field '" + fieldName + "' was not found in " + indexFacts);
            } else {
                return WandType.create(query.properties().getString(WAND_TYPE, "vespa"));
            }
        }

        private int resolveHeapSize(Query query) {
            String defaultHeapSize = "100";
            return Integer.parseInt(query.properties().getString(WAND_HEAP_SIZE, defaultHeapSize));
        }

        private double resolveScoreThreshold(Query query) {
            return Double.parseDouble(query.properties().getString(WAND_SCORE_THRESHOLD, "0"));
        }

        private double resolveThresholdBoostFactor(Query query) {
            return Double.parseDouble(query.properties().getString(WAND_THRESHOLD_BOOST_FACTOR, "1"));
        }

        public boolean hasValidData() {
            return tokens != null && !tokens.isEmpty();
        }

        public String getFieldName() {
            return fieldName;
        }

        public Map<Object, Integer> getTokens() {
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
            return populate(new WandItem(inputs.getFieldName(), inputs.getHeapSize(), inputs.getTokens()),
                            inputs.getScoreThreshold(), inputs.getThresholdBoostFactor());
        } else if (inputs.getWandType().equals(WandType.DOT_PRODUCT)) {
            return new DotProductItem(inputs.getFieldName(), inputs.getTokens());
        }
        throw new IllegalInputException("Unknown type '" + inputs.getWandType() + "'");
    }

    private CompositeItem populate(CompositeItem parent, String fieldName, Map<Object,Integer> tokens) {
        for (Map.Entry<Object,Integer> entry : tokens.entrySet()) {
            WordItem wordItem = new WordItem(entry.getKey().toString(), fieldName);
            wordItem.setWeight(entry.getValue());
            wordItem.setStemmed(true);
            wordItem.setNormalizable(false);
            parent.addItem(wordItem);
        }
        return parent;
    }

    private WandItem populate(WandItem item, double scoreThreshold, double thresholdBoostFactor) {
        item.setScoreThreshold(scoreThreshold);
        item.setThresholdBoostFactor(thresholdBoostFactor);
        return item;
    }

    private static class MapObjectIntegerParser extends SimpleMapParser {
        protected Map<Object, Integer> map;

        public Map<Object,Integer> parse(String string, Map<Object,Integer> map) {
            this.map = map;
            parse(string);
            return this.map;
        }

        @Override
        protected void handleKeyValue(String key, String value) {
            map.put(key, Integer.parseInt(value));
        }
    }

    private static class LongIntegerMapParser extends MapObjectIntegerParser {

        @Override
        protected void handleKeyValue(String key, String value) {
            map.put(Long.parseLong(key), Integer.parseInt(value));
        }

    }

}
