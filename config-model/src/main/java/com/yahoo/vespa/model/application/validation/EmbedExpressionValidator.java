// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.vespa.indexinglanguage.ExpressionVisitor;
import com.yahoo.vespa.indexinglanguage.expressions.EmbedExpression;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;

import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Validates that all embedder ids specified in 'embed' expressions references an existing component.
 *
 * @author bjorncs
 */
public class EmbedExpressionValidator implements Validator {

    private static final Logger log = Logger.getLogger(EmbedExpressionValidator.class.getName());

    @Override
    public void validate(Validation.Context context) {
        // Collect all embedder ids from EmbedExpression instances in all schemas
        var fieldToEmbedderId = new HashMap<String, String>();
        context.model().getContentClusters().forEach((__, contentCluster) -> {
            if (!contentCluster.getSearch().hasSearchCluster()) return;

            contentCluster.getSearch().getSearchCluster().schemas().forEach((___, schema) -> {
                schema.fullSchema().allFields()
                        .forEach(field -> {
                            if (field.isImportedField() || field.getIndexingScript().isEmpty()) return;

                            var visitor = new ExpressionVisitor() {
                                @Override
                                protected void doVisit(Expression e) {
                                    if (e instanceof EmbedExpression ee) {
                                        ee.requestedEmbedderId().ifPresent(id -> {
                                            var fieldName = field.getName();
                                            log.log(Level.FINE, () -> "Found embedder '%s' for field '%s'".formatted(id, fieldName));
                                            fieldToEmbedderId.put(fieldName, id);
                                        });
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
                            log.log(Level.FINE, () -> "Found component id '%s'".formatted(id));
                            allComponentIds.add(id);
                        }));

        // Validate that all embedder ids are present as components
        fieldToEmbedderId.forEach((fieldName, requestedEmbedderId) -> {
            if (!allComponentIds.contains(requestedEmbedderId)) {
                context.illegal(
                        ("The 'embed' expression for field '%s' refers to an embedder with id '%s'. " +
                                "No component with that id is configured.").formatted(fieldName, requestedEmbedderId));
            }
        });
    }
}
