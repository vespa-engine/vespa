// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.searchers;

import com.yahoo.api.annotations.Beta;
import com.yahoo.data.access.Inspectable;
import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.Type;
import com.yahoo.data.access.simple.Value;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.FeatureData;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorAddress;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * Removes all but the top-n highest scoring entries for a string array field.
 * The scores should be computed in a tensor returned in summary-features.
 *
 * @author andreer
 */
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
        String chunkFieldNames = query.properties().getString(CHUNK_LIMIT_FIELD);
        String chunkScoresFieldName = query.properties().getString(CHUNK_LIMIT_TENSOR);
        if (chunkLimit == 0 || chunkFieldNames == null || chunkScoresFieldName == null) return;

        query.trace("Pruning excessive chunks from result", 2);
        Iterator<Hit> hitIterator = result.hits().unorderedDeepIterator();
        while (hitIterator.hasNext()) {
            Hit hit = hitIterator.next();
            Set<Integer> topChunkIndexes = topChunkIndices(hit, query, chunkLimit, chunkScoresFieldName);
            if (topChunkIndexes == null) continue;
            limitChunks(hit, query, chunkFieldNames.split(","), topChunkIndexes);
        }
    }

    private Set<Integer> topChunkIndices(Hit hit, Query query, int chunkLimit, String chunkScoresFieldName) {
        FeatureData summaryFeatures = (FeatureData)hit.getField("summaryfeatures");
        if (summaryFeatures == null) {
            query.trace("No summaryfeatures found for hit " + hit.getDisplayId() + ", not limiting", 2);
            return null;
        }
        Tensor chunkScores = summaryFeatures.getTensor(chunkScoresFieldName);
        if (chunkScores == null) {
            query.trace("Field '" + chunkScoresFieldName + "' not present for hit " + hit.getDisplayId() + ", not limiting", 2);
            return null;
        }
        if (chunkScores.isEmpty()) {
            query.trace("Field '" + chunkScoresFieldName + "' has no entries for hit " + hit.getDisplayId() + ", not limiting", 2);
            return null;
        }

        return chunkScoresAsMap(chunkScores).entrySet().stream()
                                                       .sorted((b, a) -> a.getValue().compareTo(b.getValue()))
                                                       .limit(chunkLimit)
                                                       .map(Map.Entry::getKey).map(Long::intValue)
                                                       .collect(Collectors.toSet());
    }

    private void limitChunks(Hit hit, Query query, String[] chunkFieldNames, Set<Integer> topChunkIndexes) {
        for (String fieldName : chunkFieldNames) {
            fieldName = fieldName.trim();
            if (fieldName.startsWith("summaryfeatures.") || fieldName.startsWith("matchfeatures."))
                limitChunkFeature(hit, query, fieldName, topChunkIndexes);
            else
                limitChunkField(hit, query, fieldName, topChunkIndexes);
        }
    }

    private void limitChunkFeature(Hit hit, Query query, String fieldName, Set<Integer> topChunkIndexes) {
        String[] components = fieldName.split("\\.");
        FeatureData features = (FeatureData)hit.getField(components[0]);
        if (features == null) return;
        Tensor tensor = features.getTensor(components[1]);
        if (tensor == null) return;
        if (tensor.type().rank() != 1) {
            query.trace("Tensor '" + fieldName + "' doesn't have a single mapped dimension, but has type " +
                        tensor.type() + ". Not limiting.", 2);
        }
        if (tensor.size() <= topChunkIndexes.size()) return;
        Tensor.Builder b = Tensor.Builder.of(tensor.type());
        for (Integer index : topChunkIndexes)
            b.cell(tensor.get(TensorAddress.of(index)), index);
        features.set(components[1], b.build());
    }

    private void limitChunkField(Hit hit, Query query, String fieldName, Set<Integer> topChunkIndexes) {
        var chunks = ((Inspectable) hit.getField(fieldName)).inspect();
        if (chunks.type() != Type.ARRAY) return;
        if (chunks.entryCount() <= topChunkIndexes.size()) return;
        var limitedChunks = new Value.ArrayValue(topChunkIndexes.size());
        for (int i = 0; i < chunks.entryCount(); i++) {
            if (topChunkIndexes.contains(i))
                limitedChunks.add(chunks.entry(i));
        }
        hit.setField(fieldName, limitedChunks);
        if (query.getTrace().isTraceable(3))
            query.trace("limited '" + fieldName + "' in " + hit.getDisplayId() +
                        " to " + limitedChunks.entryCount() +
                        " chunks, down from " + chunks.entryCount(), 3);
    }

    private Map<Long, Double> chunkScoresAsMap(Tensor chunkLimitTensor) {
        Map<Long, Double> chunkScores = new HashMap<>();
        chunkLimitTensor.cellIterator()
                .forEachRemaining(cell -> chunkScores.put(cell.getKey().numericLabel(0), cell.getValue()));
        return chunkScores;
    }

}
