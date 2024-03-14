// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ConfigChangeAction;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.application.validation.Validation.ChangeContext;
import com.yahoo.vespa.model.application.validation.change.search.DocumentDatabaseChangeValidator;
import com.yahoo.vespa.model.content.ContentSearchCluster;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.search.DocumentDatabase;
import com.yahoo.vespa.model.search.IndexedSearchCluster;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Validates the changes between all current and next indexed search clusters in a vespa model.
 *
 * @author geirst
 */
public class IndexedSearchClusterChangeValidator implements ChangeValidator {

    @Override
    public void validate(ChangeContext context) {
        for (Map.Entry<String, ContentCluster> currentEntry : context.previousModel().getContentClusters().entrySet()) {
            ContentCluster nextCluster = context.model().getContentClusters().get(currentEntry.getKey());
            if (nextCluster != null && nextCluster.getSearch().hasIndexedCluster()) {
                validateContentCluster(currentEntry.getValue(), nextCluster, context.deployState()).forEach(context::require);
            }
        }
    }

    private static List<ConfigChangeAction> validateContentCluster(ContentCluster currentCluster,
                                                                   ContentCluster nextCluster,
                                                                   DeployState deployState)
    {
        return validateDocumentDatabases(currentCluster, nextCluster, deployState);
    }

    private static List<ConfigChangeAction> validateDocumentDatabases(ContentCluster currentCluster,
                                                                      ContentCluster nextCluster,
                                                                      DeployState deployState)
    {
        List<ConfigChangeAction> result = new ArrayList<>();
        for (DocumentDatabase currentDb : getDocumentDbs(currentCluster.getSearch())) {
            String docTypeName = currentDb.getName();
            var nextDb = nextCluster.getSearch().getIndexed().getDocumentDB(docTypeName);
            if (nextDb != null) {
                result.addAll(validateDocumentDatabase(currentCluster, nextCluster, docTypeName,
                                                       currentDb, nextDb, deployState));
            }
        }
        return result;
    }

    private static List<ConfigChangeAction> validateDocumentDatabase(ContentCluster currentCluster,
                                                                     ContentCluster nextCluster,
                                                                     String docTypeName,
                                                                     DocumentDatabase currentDb,
                                                                     DocumentDatabase nextDb,
                                                                     DeployState deployState)
    {
        NewDocumentType currentDocType = currentCluster.getDocumentDefinitions().get(docTypeName);
        NewDocumentType nextDocType = nextCluster.getDocumentDefinitions().get(docTypeName);
        List<VespaConfigChangeAction> result =
                new DocumentDatabaseChangeValidator(currentCluster.id(), currentDb, currentDocType,
                                                    nextDb, nextDocType, deployState).validate();

        return modifyActions(result, getSearchNodeServices(nextCluster.getSearch().getIndexed()), docTypeName);
    }

    private static List<DocumentDatabase> getDocumentDbs(ContentSearchCluster cluster) {
        if (cluster.getIndexed() != null) {
            return cluster.getIndexed().getDocumentDbs();
        }
        return new ArrayList<>();
    }

    private static List<ServiceInfo> getSearchNodeServices(IndexedSearchCluster cluster) {
        return cluster.getSearchNodes().stream().map(AbstractService::getServiceInfo).toList();
    }

    private static List<ConfigChangeAction> modifyActions(List<VespaConfigChangeAction> result,
                                                          List<ServiceInfo> services,
                                                          String docTypeName) {
        return result.stream()
                     .map(action -> action.modifyAction("Document type '" + docTypeName + "': " + action.getMessage(),
                                                        services, docTypeName))
                     .collect(Collectors.toList());
    }

}
