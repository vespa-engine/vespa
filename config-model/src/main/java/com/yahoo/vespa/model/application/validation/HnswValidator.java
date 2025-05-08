// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.schema.derived.SchemaInfo;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.vespa.model.application.validation.Validation.Context;
import com.yahoo.vespa.model.content.ContentSearchCluster;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

/**
 * Validates hnsw settings and warns if using hnsw and having searchable copies > 1 for a content cluster
 *
 * @author hmusum
 */
public class HnswValidator implements Validator {

    @Override
    public void validate(Context context) {
        var clusters = clustersWithMoreThanOneSearchableCopy(context);
        if (clusters.isEmpty()) return;

        clusters.forEach(contentCluster -> validateCluster(context, contentCluster.getSearch()));
    }

    // Prerequisite: content cluster has searchable copies > 1
    private static void validateCluster(Context context, ContentSearchCluster cluster) {
        if ( ! cluster.hasSearchCluster()) return;

        List<String> fields = cluster.getSearchCluster()
                                     .schemas()
                                     .values()
                                     .stream()
                                     .map(HnswValidator::fieldsWithHnswIndex)
                                     .filter(Optional::isPresent)
                                     .map(Optional::get)
                                     .toList();
        if (fields.isEmpty()) return;

        var message = ("Cluster '%s' has searchable copies > 1 and fields with hnsw index:" +
                " %s." +
                " This will use a lot of resources, consider using searchable-copies=1%s")
                .formatted(cluster.getClusterName(),
                           String.join(", ", fields),
                           cluster.usesHierarchicDistribution()
                                   ? ""
                                   : " and going to a grouped setup, see https://docs.vespa.ai/en/elasticity.html#grouped-distribution");
        context.deployState()
               .getDeployLogger()
               .logApplicationPackage(INFO, message);
    }

    private static Optional<String> fieldsWithHnswIndex(SchemaInfo schema) {
        var fields = schema.fullSchema()
                           .allFields()
                           .filter(HnswValidator::hasHnswIndex)
                           .map(ImmutableSDField::getName)
                           .sorted()
                           .toList();
        if (fields.isEmpty()) return Optional.empty();

        return Optional.of("fields %s in schema %s".formatted(String.join(", ", fields), schema.name()));
    }

    private static Set<ContentCluster> clustersWithMoreThanOneSearchableCopy(Context context) {
        return context.model()
                      .getContentClusters()
                      .values()
                      .stream()
                      .filter(c -> c.getRedundancy().readyCopies() > 1)
                      .collect(Collectors.toSet());
    }

    private static boolean hasHnswIndex(ImmutableSDField field) {
        return field.getAttributes().values().stream()
                .anyMatch(a -> a.hnswIndexParams().isPresent());
    }

}
