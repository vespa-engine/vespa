// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.searchers;

import com.yahoo.api.annotations.Beta;
import com.yahoo.data.access.simple.Value;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.FeatureData;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.tensor.Tensor;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/// Removes all but the top-n best array entries for a string array field.
/// Best is defined as highest scoring, according to a tensor produced by summary-features.
///
/// @author andreer
@Beta
public class ChunkLimitingSearcher extends Searcher {

    private static final CompoundName CHUNK_LIMIT_MAX = CompoundName.from("chunk.limit.max");
    private static final CompoundName CHUNK_LIMIT_FIELD = CompoundName.from("chunk.limit.field");
    private static final CompoundName CHUNK_LIMIT_TENSOR = CompoundName.from("chunk.limit.tensor");

    @Override
    public Result search(Query query, Execution execution) {
        return execution.search(query);
    }

    @Override
    public void fill(Result result, String summaryClass, Execution execution) {
        super.fill(result, summaryClass, execution);

        Query query = result.getQuery();

        int chunkLimit = query.properties().getInteger(CHUNK_LIMIT_MAX, 0);
        String chunkLimitedField = query.properties().getString(CHUNK_LIMIT_FIELD);
        String chunkLimitTensor = query.properties().getString(CHUNK_LIMIT_TENSOR);

        query.trace("Pruning excessive chunks from result", 2);
        result.hits().deepIterator().forEachRemaining(hit -> limitChunks(hit, query, chunkLimit, chunkLimitedField, chunkLimitTensor));
    }

    private void limitChunks(Hit hit, Query query, int chunkLimit, String chunkLimitedField, String chunkLimitTensor) {

        FeatureData summaryFeatures = (FeatureData) hit.getField("summaryfeatures");
        if (summaryFeatures == null) {
            query.trace("No summary-features found for hit " + hit.getDisplayId() + ", leaving as-is", 2);
            return;
        }

        var chunkScores = getChunkScores(summaryFeatures, chunkLimitTensor);
        var chunks = (Value.ArrayValue) hit.getField(chunkLimitedField);

        var limitedChunks = new Value.ArrayValue();

        Set<Integer> topChunkIndices = chunkScores.entrySet().stream()
                .sorted((b, a) -> a.getValue().compareTo(b.getValue()))
                .limit(chunkLimit)
                .map(Map.Entry::getKey).map(Long::intValue)
                .collect(Collectors.toSet());

        for (int i = 0; i < chunks.entryCount(); i++) {
            if(topChunkIndices.contains(i)) {
                limitedChunks.add(chunks.entry(i).asString());
            } else {
                limitedChunks.add("");
            }
        }

        hit.setField(chunkLimitedField, limitedChunks);
    }

    private TreeMap<Long, Double> getChunkScores(FeatureData summaryFeatures, String chunkLimitTensorName) {
        TreeMap<Long, Double> paragraphSimilarities = new TreeMap<>();

        Tensor chunkLimitTensor = summaryFeatures.getTensor(chunkLimitTensorName);
        chunkLimitTensor.cellIterator()
                .forEachRemaining(cell -> paragraphSimilarities.put(cell.getKey().numericLabel(0), cell.getValue()));

        return paragraphSimilarities;
    }
}
