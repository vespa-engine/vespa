// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.searchers;

import com.yahoo.api.annotations.Beta;
import com.yahoo.data.access.Inspectable;
import com.yahoo.data.access.simple.Value;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.FeatureData;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.tensor.Tensor;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/// Removes all but the top-n highest scoring entries for a string array field.
/// The scores should be computed in a tensor returned in summary-features.
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

        if (chunkLimit == 0 || chunkLimitedField == null || chunkLimitTensor == null) return;

        query.trace("Pruning excessive chunks from result", 2);
        Iterator<Hit> hitIterator = result.hits().unorderedDeepIterator();
        while(hitIterator.hasNext()) {
            Hit hit = hitIterator.next();
            limitChunks(hit, query, chunkLimit, chunkLimitedField, chunkLimitTensor);
        }
    }

    private void limitChunks(Hit hit, Query query, int chunkLimit, String chunkLimitedField, String chunkLimitTensor) {

        var chunks = ((Inspectable) hit.getField(chunkLimitedField)).inspect();
        if(chunks.entryCount() <= chunkLimit) return;

        FeatureData summaryFeatures = (FeatureData) hit.getField("summaryfeatures");
        if (summaryFeatures == null) {
            query.trace("No summaryfeatures found for hit " + hit.getDisplayId() + ", not limiting", 2);
            return;
        }

        var chunkScores = getChunkScores(summaryFeatures, chunkLimitTensor);
        if(chunkScores == null) {
            query.trace("chunk.limit.tensor not present for hit " + hit.getDisplayId() + ", not limiting", 2);
            return;
        }

        if(chunkScores.size() != chunks.entryCount()) {
            query.trace("chunk.limit.tensor has wrong number of entries for hit " + hit.getDisplayId() + ", not limiting", 2);
            return;
        }

        Set<Integer> topChunkIndices = chunkScores.entrySet().stream()
                .sorted((b, a) -> a.getValue().compareTo(b.getValue()))
                .limit(chunkLimit)
                .map(Map.Entry::getKey).map(Long::intValue)
                .collect(Collectors.toSet());

        var limitedChunks = new Value.ArrayValue();
        for (int i = 0; i < chunks.entryCount(); i++) {
            if(topChunkIndices.contains(i)) limitedChunks.add(chunks.entry(i).asString());
        }
        hit.setField(chunkLimitedField, limitedChunks);

        query.trace("limited " + hit.getDisplayId() + " to " + limitedChunks.entryCount() + " chunks down from " + chunks.entryCount(), 3);
    }

    private TreeMap<Long, Double> getChunkScores(FeatureData summaryFeatures, String chunkLimitTensorName) {
        TreeMap<Long, Double> paragraphSimilarities = new TreeMap<>();

        Tensor chunkLimitTensor = summaryFeatures.getTensor(chunkLimitTensorName);

        if(chunkLimitTensor == null) {
            return null;
        }

        chunkLimitTensor.cellIterator()
                .forEachRemaining(cell -> paragraphSimilarities.put(cell.getKey().numericLabel(0), cell.getValue()));

        return paragraphSimilarities;
    }
}