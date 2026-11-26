// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.language.process.Embedder;
import com.yahoo.text.Text;
import com.yahoo.vespa.indexinglanguage.ExpressionVisitor;
import com.yahoo.vespa.indexinglanguage.expressions.EmbedExpression;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.model.container.component.EmbedderComponent;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Validates that all embedder ids specified in 'embed' expressions references an existing component.
 * Also validates that an embedder provided by Vespa accepts the input it is given.
 *
 * @author bjorncs
 * @author glebashnik
 */
public class EmbedExpressionValidator implements Validator {

    private static final Logger log = Logger.getLogger(EmbedExpressionValidator.class.getName());

    @Override
    public void validate(Validation.Context context) {
        record SchemaAndField(String schemaName, String fieldName) {}

        // Collect all embedder ids from EmbedExpression instances in all schemas
        var fieldToEmbedderId = new HashMap<SchemaAndField, String>();
        context.model().getContentClusters().forEach((__, contentCluster) -> {
            if (!contentCluster.getSearch().hasSearchCluster()) return;
            var indexingEmbedders = embeddersOfIndexingCluster(contentCluster, context);

            contentCluster.getSearch().getSearchCluster().schemas().forEach((___, schema) -> {
                // allFields() lists a field outside the document twice:
                // as an extra field, and as a field of the document type
                var visitedFieldNames = new HashSet<String>();
                schema.fullSchema().allFields()
                        .forEach(field -> {
                            if (field.isImportedField() || field.getIndexingScript().isEmpty()) return;
                            if ( ! visitedFieldNames.add(field.getName())) return;

                            var visitor = new ExpressionVisitor() {
                                @Override
                                protected void doVisit(Expression e) {
                                    if (e instanceof EmbedExpression ee) {
                                        var fieldName = field.getName();
                                        var schemaName = schema.fullSchema().getName();
                                        ee.requestedEmbedderId().ifPresent(id -> {
                                            log.log(Level.FINE, () -> Text.format("Found embedder '%s' for field '%s' in schema '%s'", id, fieldName, schemaName));
                                            fieldToEmbedderId.put(new SchemaAndField(schemaName, fieldName), id);
                                        });
                                        validateInput(ee, fieldName, schemaName, indexingEmbedders, context);
                                    }
                                }
                            };
                            visitor.visit(field.getIndexingScript());
                        });
            });
        });

        // Collect all component ids from the model
        var allComponentIds = new HashSet<String>();
        context.model().getContainerClusters().forEach((__, containerCluster) ->
                containerCluster.getAllComponents()
                        .forEach(component -> {
                            var id = component.getComponentId().getName();
                            log.log(Level.FINE, () -> Text.format("Found component id '%s'", id));
                            allComponentIds.add(id);
                        }));

        // Validate that all embedder ids are present as components
        fieldToEmbedderId.forEach((field, requestedEmbedderId) -> {
            if (!allComponentIds.contains(requestedEmbedderId)) {
                context.illegal(
                        Text.format("The 'embed' expression for field '%s' in schema '%s' refers to an embedder with id '%s'. " + "No component with that id is configured.", field.fieldName(), field.schemaName(), requestedEmbedderId));
            }
        });
    }

    /** Reports an 'embed' expression whose embedder does not accept its input, for an embedder provided by Vespa. */
    private static void validateInput(EmbedExpression embed, String fieldName, String schemaName,
                                      Map<String, EmbedderComponent> indexingEmbedders, Validation.Context context) {
        // With no embedder id, 'embed' uses the only embedder of its cluster
        String onlyEmbedderId = indexingEmbedders.size() == 1 ? indexingEmbedders.keySet().iterator().next() : null;
        String embedderId = embed.requestedEmbedderId().orElse(onlyEmbedderId);
        if (embedderId == null) return; // No embedder id, and not exactly one embedder to use instead

        EmbedderComponent embedder = indexingEmbedders.get(embedderId);
        if (embedder == null) return; // Not an embedder provided by Vespa, so its supported modalities are not known

        embed.embedderInputModalities()
             .flatMap(inputModalities -> Embedder.rejectionReason(inputModalities, embedder.supportedModalities()))
             .ifPresent(rejection -> context.illegal(
                     Text.format("The 'embed' expression for field '%s' in schema '%s' uses the embedder " +
                                 "with id '%s', which %s.", fieldName, schemaName, embedderId, rejection)));
    }

    /**
     * Returns the embedders provided by Vespa, by id, in the container cluster which indexes the documents
     * of the given content cluster. Another container cluster may have another embedder with the same id.
     */
    private static Map<String, EmbedderComponent> embeddersOfIndexingCluster(ContentCluster contentCluster,
                                                                             Validation.Context context) {
        String indexingClusterName = contentCluster.getSearch().getIndexingDocproc().getClusterName(contentCluster.getName());
        var indexingCluster = context.model().getContainerClusters().get(indexingClusterName);
        if (indexingCluster == null) return Map.of();
        var embedders = new HashMap<String, EmbedderComponent>();
        indexingCluster.getAllComponents().forEach(component -> {
            if (component instanceof EmbedderComponent embedder)
                embedders.put(component.getComponentId().getName(), embedder);
        });
        return embedders;
    }

}
