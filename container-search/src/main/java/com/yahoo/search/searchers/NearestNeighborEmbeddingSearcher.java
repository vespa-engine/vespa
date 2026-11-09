// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.language.process.Embedder;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.NearestNeighborItem;
import com.yahoo.prelude.query.ToolBox;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.grouping.vespa.GroupingExecutor;
import com.yahoo.search.query.ranking.RankFeatures;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.schema.Field;
import com.yahoo.search.schema.RankProfile;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.schema.internal.TensorConverter;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import com.yahoo.yolean.Exceptions;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Embeds query inputs which are declared as tensors but given a text value, when they are used as the
 * query tensor of a nearestNeighbor item: The text is embedded in the same way as the searched field is
 * embedded during indexing.
 *
 * @author bratseth
 */
@Before({GroupingExecutor.COMPONENT_NAME, "com.yahoo.search.searchers.ValidateNearestNeighborSearcher" })
public class NearestNeighborEmbeddingSearcher extends Searcher {

    private final SchemaInfo schemaInfo;
    private final TensorConverter tensorConverter;

    @Inject
    public NearestNeighborEmbeddingSearcher(SchemaInfo schemaInfo, ComponentRegistry<Embedder> embedders) {
        this(schemaInfo, TensorConverter.toMap(embedders));
    }

    NearestNeighborEmbeddingSearcher(SchemaInfo schemaInfo, Map<String, Embedder> embedders) {
        this.schemaInfo = schemaInfo;
        this.tensorConverter = new TensorConverter(embedders);
    }

    @Override
    public Result search(Query query, Execution execution) {
        try {
            embedTextInputs(query);
        }
        catch (IllegalArgumentException e) {
            return new Result(query, ErrorMessage.createIllegalQuery(Exceptions.toMessageString(e)));
        }
        return execution.search(query);
    }

    private void embedTextInputs(Query query) {
        RankFeatures features = query.getRanking().getFeatures();
        if (features.asMap().values().stream().noneMatch(value -> value instanceof String)) return;

        var session = schemaInfo.newSession(query);
        String profile = query.getRanking().getProfile();
        Map<String, String> inputExpressions = inputExpressionsOfTextInputs(query, features, session);
        if ( ! inputExpressions.isEmpty()) {
            long timeLeftMs = query.getTimeLeft();
            Instant deadline = timeLeftMs > 0 ? Instant.now().plusMillis(timeLeftMs) : null;
            for (var entry : inputExpressions.entrySet()) {
                String feature = entry.getKey();
                RankProfile.InputType type = session.rankProfileInput(feature, profile);
                if (type == null || type.declaredString()) continue;
                features.put(feature, tensorConverter.embedInput(entry.getValue(),
                                                                 (String)features.getObject(feature),
                                                                 type.tensorType(),
                                                                 feature,
                                                                 query.getModel().getLanguage(),
                                                                 deadline));
            }
        }
        requireNoTextTensorInputs(features, session, profile);
    }

    /**
     * Returns the input expressions to use to embed each feature having a text value
     * which is used as the query tensor of a nearestNeighbor item searching a field having an input expression.
     */
    private Map<String, String> inputExpressionsOfTextInputs(Query query, RankFeatures features, SchemaInfo.Session session) {
        Map<String, String> inputExpressions = new LinkedHashMap<>();
        ToolBox.visit(new ToolBox.QueryVisitor() {
            @Override
            public boolean visit(Item item) {
                if ( ! (item instanceof NearestNeighborItem nnItem)) return true;
                String feature = "query(" + nnItem.getQueryTensorName() + ")";
                if ( ! (features.getObject(feature) instanceof String)) return true;
                inputExpression(nnItem.getIndexName(), session).ifPresent(inputExpression -> {
                    String existing = inputExpressions.putIfAbsent(feature, inputExpression);
                    if (existing != null && ! existing.equals(inputExpression))
                        throw new IllegalArgumentException("The text of '" + feature + "' is searched in fields " +
                                                           "which are embedded differently: Using both " + existing +
                                                           " and " + inputExpression);
                });
                return true;
            }
        }, query.getModel().getQueryTree().getRoot());
        return inputExpressions;
    }

    /** Returns the input expression of the given field, if it has one in the schemas of the session. */
    private Optional<String> inputExpression(String fieldName, SchemaInfo.Session session) {
        Set<String> inputExpressions = new TreeSet<>();
        for (var schema : session.schemas()) {
            if (schema.fieldInfo(fieldName).orElse(null) instanceof Field field)
                field.inputExpression().ifPresent(inputExpressions::add);
        }
        if (inputExpressions.size() > 1)
            throw new IllegalArgumentException("Field '" + fieldName + "' is embedded differently in the schemas " +
                                               "searched: Using " + String.join(", and ", inputExpressions));
        return inputExpressions.stream().findFirst();
    }

    /** Throws if any input declared as a tensor still has a text value. */
    private void requireNoTextTensorInputs(RankFeatures features, SchemaInfo.Session session, String profile) {
        for (var entry : features.asMap().entrySet()) {
            if ( ! (entry.getValue() instanceof String text)) continue;
            RankProfile.InputType type = session.rankProfileInput(entry.getKey(), profile);
            if (type == null || type.declaredString()) continue;
            throw new IllegalArgumentException("Could not set '" + entry.getKey() + "' to '" + text + "': " +
                                               "This is not a valid " + type.tensorType() +
                                               parseError(type.tensorType(), text) +
                                               ", and it is not the query tensor of a nearestNeighbor item " +
                                               "searching a field which is embedded");
        }
    }

    private static String parseError(TensorType type, String text) {
        try {
            Tensor.from(type, text);
            return "";
        }
        catch (IllegalArgumentException e) {
            return " (" + Exceptions.toMessageString(e) + ")";
        }
    }

}
