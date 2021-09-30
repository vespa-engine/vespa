// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.vespa.model.application.validation.change.search.DocumentDatabaseChangeValidator;
import com.yahoo.vespa.model.content.ContentSearchCluster;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.search.DocumentDatabase;
import com.yahoo.vespa.model.search.IndexedSearchCluster;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Validates the changes between all current and next indexed search clusters in a vespa model.
 *
 * @author geirst
 */
public class IndexedSearchClusterChangeValidator implements ChangeValidator {

    @Override
    public List<ConfigChangeAction> validate(VespaModel current, VespaModel next, ValidationOverrides overrides, Instant now) {
        List<ConfigChangeAction> result = new ArrayList<>();
        for (Map.Entry<String, ContentCluster> currentEntry : current.getContentClusters().entrySet()) {
            ContentCluster nextCluster = next.getContentClusters().get(currentEntry.getKey());
            if (nextCluster != null && nextCluster.getSearch().hasIndexedCluster()) {
                result.addAll(validateContentCluster(currentEntry.getValue(), nextCluster));
            }
        }
        return result;
    }

    private static List<ConfigChangeAction> validateContentCluster(ContentCluster currentCluster,
                                                                   ContentCluster nextCluster) {
        return validateDocumentDatabases(currentCluster, nextCluster);
    }

    private static List<ConfigChangeAction> validateDocumentDatabases(ContentCluster currentCluster,
                                                                      ContentCluster nextCluster) {
        List<ConfigChangeAction> result = new ArrayList<>();
        for (DocumentDatabase currentDb : getDocumentDbs(currentCluster.getSearch())) {
            String docTypeName = currentDb.getName();
            Optional<DocumentDatabase> nextDb = nextCluster.getSearch().getIndexed().getDocumentDbs().stream().
                                                            filter(db -> db.getName().equals(docTypeName)).findFirst();
            if (nextDb.isPresent()) {
                result.addAll(validateDocumentDatabase(currentCluster, nextCluster, docTypeName,
                                                       currentDb, nextDb.get()));
            }
        }
        return result;
    }

    private static List<ConfigChangeAction> validateDocumentDatabase(ContentCluster currentCluster,
                                                                     ContentCluster nextCluster,
                                                                     String docTypeName,
                                                                     DocumentDatabase currentDb,
                                                                     DocumentDatabase nextDb) {
        NewDocumentType currentDocType = currentCluster.getDocumentDefinitions().get(docTypeName);
        NewDocumentType nextDocType = nextCluster.getDocumentDefinitions().get(docTypeName);
        List<VespaConfigChangeAction> result =
                new DocumentDatabaseChangeValidator(currentCluster.id(), currentDb, currentDocType, nextDb, nextDocType).validate();

        return modifyActions(result, getSearchNodeServices(nextCluster.getSearch().getIndexed()), docTypeName);
    }

    private static List<DocumentDatabase> getDocumentDbs(ContentSearchCluster cluster) {
        if (cluster.getIndexed() != null) {
            return cluster.getIndexed().getDocumentDbs();
        }
        return new ArrayList<>();
    }

    private static List<ServiceInfo> getSearchNodeServices(IndexedSearchCluster cluster) {
        return cluster.getSearchNodes().stream().
                map(node -> node.getServiceInfo()).
                collect(Collectors.toList());
    }

    private static List<ConfigChangeAction> modifyActions(List<VespaConfigChangeAction> result,
                                                          List<ServiceInfo> services,
                                                          String docTypeName) {
        return result.stream().
                map(action -> action.modifyAction("Document type '" + docTypeName + "': " + action.getMessage(),
                                                  services, docTypeName)).
                collect(Collectors.toList());
    }

}
