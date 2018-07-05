// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.document.*;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.search.AbstractSearchCluster;
import com.yahoo.vespa.model.search.SearchDefinition;

import java.util.List;

/**
 * This Validator iterates through all search cluster in the given VespaModel to make sure that there are no custom
 * structs defined in any of its search definitions.
 *
 * @author Simon Thoresen Hult
 */
public class SearchDataTypeValidator extends Validator {

    @Override
    public void validate(VespaModel model, DeployState deployState) {
        List<AbstractSearchCluster> clusters = model.getSearchClusters();
        for (AbstractSearchCluster cluster : clusters) {
            if (cluster.isStreaming()) {
                continue;
            }
            for (AbstractSearchCluster.SearchDefinitionSpec spec : cluster.getLocalSDS()) {
                SDDocumentType docType = spec.getSearchDefinition().getSearch().getDocument();
                if (docType == null) {
                    continue;
                }
                validateDocument(cluster, spec.getSearchDefinition(), docType);
            }
        }
    }

    private void validateDocument(AbstractSearchCluster cluster, SearchDefinition def, SDDocumentType doc) {
        for (SDDocumentType child : doc.getTypes()) {
            validateDocument(cluster, def, child);
        }
        for (Field field : doc.fieldSet()) {
            DataType fieldType = field.getDataType();
            disallowIndexingOfMaps(cluster, def, field);
            if ( ! isSupportedInSearchClusters(fieldType)) {
                throw new IllegalArgumentException("Field type '" + fieldType.getName() + "' is illegal for search " +
                                                   "clusters (field '" + field.getName() + "' in definition '" +
                                                   def.getName() + "' for cluster '" + cluster.getClusterName() + "').");
            }
        }
    }

    private boolean isSupportedInSearchClusters(DataType dataType) {
        if (dataType instanceof ArrayDataType || dataType instanceof WeightedSetDataType) {
            return isSupportedInSearchClusters(((CollectionDataType)dataType).getNestedType());
        } else if (dataType instanceof StructDataType) {
            return true; // Struct will work for summary TODO maybe check individual fields
        } else if (dataType instanceof MapDataType) {
            return true; // Maps will work for summary, see disallowIndexingOfMaps()
        } else if (dataType instanceof TensorDataType) {
            return true;
        } else if (dataType instanceof ReferenceDataType) {
            return true;
        } else {
            return dataType.equals(DataType.INT) ||
                   dataType.equals(DataType.FLOAT) ||
                   dataType.equals(DataType.STRING) ||
                   dataType.equals(DataType.RAW) ||
                   dataType.equals(DataType.LONG) ||
                   dataType.equals(DataType.DOUBLE) ||
                   dataType.equals(DataType.URI) ||
                   dataType.equals(DataType.BYTE) ||
                   dataType.equals(DataType.PREDICATE);
        }
    }

    private void disallowIndexingOfMaps(AbstractSearchCluster cluster, SearchDefinition def, Field field) {
        DataType fieldType = field.getDataType();
        if ((fieldType instanceof MapDataType) && (((SDField) field).doesIndexing())) {
            throw new IllegalArgumentException("Field type '" + fieldType.getName() + "' cannot be indexed for search " +
                                               "clusters (field '" + field.getName() + "' in definition '" +
                                               def.getName() + "' for cluster '" + cluster.getClusterName() + "').");
        }
    }
}
