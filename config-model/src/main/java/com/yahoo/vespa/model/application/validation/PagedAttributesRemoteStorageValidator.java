// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.schema.Schema;
import com.yahoo.schema.document.Attribute;
import com.yahoo.schema.document.ImmutableSDField;
import com.yahoo.vespa.model.application.validation.Validation.Context;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yahoo.config.provision.ClusterSpec.Type.content;
import static com.yahoo.config.provision.NodeResources.StorageType.remote;
import static java.util.logging.Level.WARNING;

/**
 * Validates and logs a warning (with deploy logger) when paged attributes are used in a
 * content cluster with nodes with remote storage
 *
 * @author hmusum
 */
public class PagedAttributesRemoteStorageValidator implements Validator {

    @Override
    public void validate(Context context) {
        var contentClustersWithRemoteStorage = context.model().allocatedHosts().getHosts().stream()
                .filter(hostSpec -> hostSpec.realResources().storageType() == remote)
                .map(HostSpec::membership)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(membership -> membership.cluster().type() == content)
                .map(membership -> membership.cluster().id().value())
                .collect(Collectors.toSet());

        for (var cluster : context.model().getSearchClusters()) {
            if ( ! contentClustersWithRemoteStorage.contains(cluster.getClusterName())) continue;

            for (var schema : cluster.schemas().values()) {
                validatePagedAttributes(context.deployState().getDeployLogger(), cluster.getClusterName(), schema.fullSchema());
            }
        }
    }

    private static void validatePagedAttributes(DeployLogger logger, String clusterName, Schema schema) {
        List<Attribute> fields = schema.allFields()
                .flatMap(field -> pagedAttributes(field).stream())
                .collect(Collectors.toList());
        if (fields.isEmpty()) return;

        logger.logApplicationPackage(WARNING, ("Cluster '%s' has nodes with remote storage and fields with paged attributes." +
                " This might lead to performance issues when doing I/O." +
                " Consider using storage-type='local' or removing 'paged' setting for these fields: %s")
                .formatted(clusterName, join(fields)));
    }

    private static String join(List<Attribute> fields) {
        var ret = fields.stream()
                .limit(10)
                .map(Attribute::getName)
                .map(s -> "'" + s + "'")
                .collect(Collectors.joining(", "));
        return ret + ((fields.size() > 10) ? ", ..." : "");
    }

    private static Set<Attribute> pagedAttributes(ImmutableSDField field) {
        return field.getAttributes().values().stream()
                .filter(Attribute::isPaged)
                .collect(Collectors.toSet());
    }

}
