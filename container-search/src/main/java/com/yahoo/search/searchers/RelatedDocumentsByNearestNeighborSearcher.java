// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.search.searchers;

import com.yahoo.api.annotations.Beta;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NearestNeighborItem;
import com.yahoo.prelude.query.NotItem;
import com.yahoo.prelude.query.NullItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.tensor.Tensor;

/**
 * Finds documents related to a given document using nearest neighbor search on embeddings.
 *
 * <p>This searcher takes a document ID, fetches the embedding from that document,
 * and performs a nearest neighbor search to find similar documents.</p>
 *
 * <h2>Query parameters:</h2>
 * <ul>
 *   <li><b>relatedTo.id</b> - The ID of the source document to find related documents for (required)</li>
 *   <li><b>relatedTo.idField</b> - The field containing the document ID (default: "id")</li>
 *   <li><b>relatedTo.embeddingField</b> - The embedding field to use for NN search (required)</li>
 *   <li><b>relatedTo.queryTensorName</b> - The name of the query tensor to use, must match rank profile (required)</li>
 *   <li><b>relatedTo.summary</b> - The summary class containing the embedding (default: same as embeddingField)</li>
 *   <li><b>relatedTo.exploreAdditionalHits</b> - Additional candidates to explore beyond hits+offset (default: 100)</li>
 *   <li><b>relatedTo.excludeSource</b> - Whether to exclude the source document from results (default: true)</li>
 * </ul>
 *
 * @author andreer
 */
@Beta
public class RelatedDocumentsByNearestNeighborSearcher extends Searcher {

    private static final CompoundName RELATED_TO_ID = CompoundName.from("relatedTo.id");
    private static final CompoundName RELATED_TO_ID_FIELD = CompoundName.from("relatedTo.idField");
    private static final CompoundName RELATED_TO_EMBEDDING_FIELD = CompoundName.from("relatedTo.embeddingField");
    private static final CompoundName RELATED_TO_QUERY_TENSOR_NAME = CompoundName.from("relatedTo.queryTensorName");
    private static final CompoundName RELATED_TO_SUMMARY = CompoundName.from("relatedTo.summary");
    private static final CompoundName RELATED_TO_EXPLORE_ADDITIONAL_HITS = CompoundName.from("relatedTo.exploreAdditionalHits");
    private static final CompoundName RELATED_TO_EXCLUDE_SOURCE = CompoundName.from("relatedTo.excludeSource");

    @Override
    public Result search(Query query, Execution execution) {
        String sourceId = query.properties().getString(RELATED_TO_ID);
        if (sourceId == null) {
            return execution.search(query);
        }

        String embeddingField = query.properties().getString(RELATED_TO_EMBEDDING_FIELD);
        if (embeddingField == null) {
            return new Result(query, ErrorMessage.createIllegalQuery(
                    "relatedTo.embeddingField is required when using relatedTo.id"));
        }

        String queryTensorName = query.properties().getString(RELATED_TO_QUERY_TENSOR_NAME);
        if (queryTensorName == null) {
            return new Result(query, ErrorMessage.createIllegalQuery(
                    "relatedTo.queryTensorName is required when using relatedTo.id"));
        }

        String idField = query.properties().getString(RELATED_TO_ID_FIELD, "id");
        String summary = query.properties().getString(RELATED_TO_SUMMARY, embeddingField);
        int targetHits = query.getHits() + query.getOffset();
        int exploreAdditionalHits = query.properties().getInteger(RELATED_TO_EXPLORE_ADDITIONAL_HITS, 100);
        boolean excludeSource = query.properties().getBoolean(RELATED_TO_EXCLUDE_SOURCE, true);

        Tensor embedding = fetchEmbedding(sourceId, idField, embeddingField, summary, execution, query);
        if (embedding == null) {
            return new Result(query, ErrorMessage.createBackendCommunicationError(
                    "Could not find document with " + idField + "=" + sourceId + " or it has no " + embeddingField));
        }

        addNearestNeighborItem(embedding, embeddingField, queryTensorName, targetHits, exploreAdditionalHits, query);

        if (excludeSource) {
            excludeSourceDocument(sourceId, idField, query);
        }

        return execution.search(query);
    }

    private Tensor fetchEmbedding(String sourceId, String idField, String embeddingField, String summary,
                                  Execution execution, Query query) {
        Query fetchQuery = new Query();
        query.attachContext(fetchQuery);
        fetchQuery.getPresentation().setSummary(summary);
        fetchQuery.getModel().getQueryTree().setRoot(new WordItem(sourceId, idField, true));
        fetchQuery.setHits(1);
        fetchQuery.getRanking().setProfile("unranked");

        Result result = execution.search(fetchQuery);
        execution.fill(result, summary);

        if (result.hits().size() < 1) {
            return null;
        }

        Hit hit = result.hits().get(0);
        Object field = hit.getField(embeddingField);
        if (field instanceof Tensor tensor) {
            return tensor;
        }
        return null;
    }

    private void addNearestNeighborItem(Tensor embedding, String embeddingField, String queryTensorName,
                                         int targetHits, int exploreAdditionalHits, Query query) {
        query.getRanking().getFeatures().put("query(" + queryTensorName + ")", embedding);

        NearestNeighborItem nnItem = new NearestNeighborItem(embeddingField, queryTensorName);
        nnItem.setAllowApproximate(true);
        nnItem.setTargetHits(targetHits);
        nnItem.setHnswExploreAdditionalHits(exploreAdditionalHits);

        Item root = query.getModel().getQueryTree().getRoot();
        if (root instanceof NullItem || root == null) {
            query.getModel().getQueryTree().setRoot(nnItem);
        } else {
            AndItem andItem = new AndItem();
            andItem.addItem(root);
            andItem.addItem(nnItem);
            query.getModel().getQueryTree().setRoot(andItem);
        }
    }

    private void excludeSourceDocument(String sourceId, String idField, Query query) {
        NotItem notItem = new NotItem();
        notItem.addPositiveItem(query.getModel().getQueryTree().getRoot());
        notItem.addNegativeItem(new WordItem(sourceId, idField, true));
        query.getModel().getQueryTree().setRoot(notItem);
    }

}
