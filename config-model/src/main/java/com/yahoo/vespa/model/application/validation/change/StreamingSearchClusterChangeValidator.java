// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change;

import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.schema.derived.AttributeFields;
import com.yahoo.schema.document.Attribute;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.application.validation.Validation.ChangeContext;
import com.yahoo.vespa.model.application.validation.change.search.ChangeMessageBuilder;
import com.yahoo.vespa.model.application.validation.change.search.DocumentTypeChangeValidator;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.search.DocumentDatabase;
import com.yahoo.vespa.model.search.SearchCluster;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Validates the changes between all current and next streaming search clusters in a Vespa model.
 *
 * @author geirst
 */
public class StreamingSearchClusterChangeValidator implements ChangeValidator {

    @Override
    public void validate(ChangeContext context) {
        context.previousModel().getContentClusters().forEach((clusterName, currentCluster) -> {
            ContentCluster nextCluster = context.model().getContentClusters().get(clusterName);
            if (nextCluster != null) {
                if (currentCluster.getSearch().getIndexed() != null && nextCluster.getSearch().getIndexed() != null) {
                    validateStreamingCluster(currentCluster, currentCluster.getSearch().getIndexed(),
                                             nextCluster, nextCluster.getSearch().getIndexed())
                            .forEach(context::require);
                }
            }
        });
    }

    private static List<VespaConfigChangeAction> validateStreamingCluster(ContentCluster currentCluster,
                                                                     SearchCluster currentStreamingCluster,
                                                                     ContentCluster nextCluster,
                                                                     SearchCluster nextStreamingCluster) {
        List<VespaConfigChangeAction> result = new ArrayList<>();

        for (DocumentDatabase currentDB : currentStreamingCluster.getDocumentDbs()) {
            DocumentDatabase nextDB = nextStreamingCluster.getDocumentDB(currentDB.getName());
            if (nextDB != null) {
                result.addAll(validateDocumentDB(currentCluster, currentDB, nextCluster, nextDB));
            }
        }
        return result;
    }

    private static List<VespaConfigChangeAction> validateDocumentDB(ContentCluster currentCluster, DocumentDatabase currentDB,
                                                               ContentCluster nextCluster, DocumentDatabase nextDB) {
        List<VespaConfigChangeAction> result = new ArrayList<>();

        result.addAll(validateDocumentTypeChanges(currentCluster.id(),
                getDocumentType(currentCluster, currentDB),
                getDocumentType(nextCluster, nextDB)));
        result.addAll(validateAttributeFastAccessAdded(currentCluster.id(),
                currentDB.getDerivedConfiguration().getAttributeFields(),
                nextDB.getDerivedConfiguration().getAttributeFields()));
        result.addAll(validateAttributeFastAccessRemoved(currentCluster.id(),
                currentDB.getDerivedConfiguration().getAttributeFields(),
                nextDB.getDerivedConfiguration().getAttributeFields()));

        return modifyActions(result, getSearchNodeServices(nextCluster), nextDB.getName());
    }

    private static List<VespaConfigChangeAction> validateDocumentTypeChanges(ClusterSpec.Id id,
                                                                             NewDocumentType currentDocType,
                                                                             NewDocumentType nextDocType) {
        return new DocumentTypeChangeValidator(id, currentDocType, nextDocType).validate();
    }

    private static NewDocumentType getDocumentType(ContentCluster cluster, DocumentDatabase db) {
        return cluster.getDocumentDefinitions().get(db.getName());
    }

    private static List<VespaConfigChangeAction> validateAttributeFastAccessAdded(ClusterSpec.Id id,
                                                                                  AttributeFields currentAttributes,
                                                                                  AttributeFields nextAttributes) {
        return validateAttributeFastAccessChanged(id, nextAttributes, currentAttributes, "add");
    }

    private static List<VespaConfigChangeAction> validateAttributeFastAccessRemoved(ClusterSpec.Id id,
                                                                                    AttributeFields currentAttributes,
                                                                                    AttributeFields nextAttributes) {
        return validateAttributeFastAccessChanged(id, currentAttributes, nextAttributes, "remove");
    }

    private static List<VespaConfigChangeAction> validateAttributeFastAccessChanged(ClusterSpec.Id id,
                                                                                    AttributeFields lhsAttributes,
                                                                                    AttributeFields rhsAttributes,
                                                                                    String change) {
        return lhsAttributes.attributes().stream()
                .filter(attr -> attr.isFastAccess() &&
                        !hasFastAccessAttribute(attr.getName(), rhsAttributes))
                .map(attr -> new VespaRestartAction(id, new ChangeMessageBuilder(attr.getName()).addChange(change + " fast-access attribute").build()))
                .collect(Collectors.toList());
    }

    private static boolean hasFastAccessAttribute(String attrName, AttributeFields attributes) {
        Attribute attr = attributes.getAttribute(attrName);
        return (attr != null && attr.isFastAccess());
    }

    private static List<ServiceInfo> getSearchNodeServices(ContentCluster cluster) {
        return cluster.getSearch().getSearchNodes().stream()
                .map(AbstractService::getServiceInfo)
                .toList();
    }

    private static List<VespaConfigChangeAction> modifyActions(List<VespaConfigChangeAction> result,
                                                          List<ServiceInfo> services,
                                                          String docTypeName) {
        return result.stream()
                .map(action -> action.modifyAction("Document type '" + docTypeName + "': " + action.getMessage(),
                                                   services, docTypeName))
                .collect(Collectors.toList());
    }

}
