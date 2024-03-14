// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.CollectionDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.documentmodel.NewDocumentReferenceDataType;
import com.yahoo.schema.Schema;
import com.yahoo.schema.derived.SchemaInfo;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.vespa.model.application.validation.Validation.Context;
import com.yahoo.vespa.model.search.SearchCluster;

import java.util.List;

/**
 * This Validator iterates through all search cluster in the given VespaModel to make sure that there are no custom
 * structs defined in any of its search definitions.
 *
 * @author Simon Thoresen Hult
 */
public class SearchDataTypeValidator implements Validator {

    @Override
    public void validate(Context context) {
        List<SearchCluster> clusters = context.model().getSearchClusters();
        for (SearchCluster cluster : clusters) {
            for (SchemaInfo spec : cluster.schemas().values()) {
                SDDocumentType docType = spec.fullSchema().getDocument();
                if (docType != null && spec.getIndexMode() == SchemaInfo.IndexMode.INDEX) {
                    validateDocument(context, cluster, spec.fullSchema(), docType);
                }
            }
        }
    }

    private void validateDocument(Context context, SearchCluster cluster, Schema schema, SDDocumentType doc) {
        for (SDDocumentType child : doc.getTypes()) {
            validateDocument(context, cluster, schema, child);
        }
        for (Field field : doc.fieldSet()) {
            DataType fieldType = field.getDataType();
            disallowIndexingOfMaps(context, cluster, schema, field);
            if ( ! isSupportedInSearchClusters(fieldType)) {
                context.illegal("Field type '" + fieldType.getName() + "' is illegal for search " +
                                "clusters (field '" + field.getName() + "' in schema '" +
                                schema.getName() + "' for cluster '" + cluster.getClusterName() + "').");
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
        } else if (dataType instanceof NewDocumentReferenceDataType) {
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
                   dataType.equals(DataType.BOOL) ||
                   dataType.equals(DataType.PREDICATE);
        }
    }

    private void disallowIndexingOfMaps(Context context, SearchCluster cluster, Schema schema, Field field) {
        DataType fieldType = field.getDataType();
        if ((fieldType instanceof MapDataType) && (((SDField) field).doesIndexing())) {
            context.illegal("Field type '" + fieldType.getName() + "' cannot be indexed for search " +
                            "clusters (field '" + field.getName() + "' in definition '" +
                            schema.getName() + "' for cluster '" + cluster.getClusterName() + "').");
        }
    }

}
