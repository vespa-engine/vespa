// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.schema.derived.SchemaInfo;
import com.yahoo.schema.document.Attribute;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.vespa.model.application.validation.Validation.Context;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

import java.util.Set;
import java.util.stream.Collectors;

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

        clusters.forEach(contentCluster -> validateCluster(context, contentCluster));
    }

    // Prerequisite: content cluster has searchable copies > 1
    private static void validateCluster(Context context, ContentCluster cluster) {
        if ( ! cluster.getSearch().hasSearchCluster()) return;

        for (var schema : cluster.getSearch()
                                 .getSearchCluster()
                                 .schemas()
                                 .values()) {
            if (warnAboutSearchableCopies(schema)) {
                var message = ("Cluster '%s' has searchable copies > 1 and fields with hnsw index." +
                        " This will use a lot of resources, consider using searchable-copies=1%s")
                        .formatted(cluster.getName(),
                                   cluster.getSearch()
                                          .usesHierarchicDistribution()
                                           ? ""
                                           : " and going to a grouped setup, see https://docs.vespa.ai/en/elasticity.html#grouped-distribution");
                context.deployState().getDeployLogger().logApplicationPackage(WARNING, message);
            }
        }
    }

    private static boolean warnAboutSearchableCopies(SchemaInfo schema) {
        return schema.fullSchema()
                     .allFields()
                     .mapToLong(field -> hnswAttributes(field).size())
                     .sum() > 0;
    }

    private static Set<ContentCluster> clustersWithMoreThanOneSearchableCopy(Context context) {
        return context.model()
                      .getContentClusters()
                      .values()
                      .stream()
                      .filter(c -> c.getRedundancy().readyCopies() > 1)
                      .collect(Collectors.toSet());
    }

    private static Set<Attribute> hnswAttributes(ImmutableSDField field) {
        return field.getAttributes().values().stream()
                .filter(a -> a.hnswIndexParams().isPresent())
                .collect(Collectors.toSet());
    }

}
