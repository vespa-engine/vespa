// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change.search;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.schema.derived.AttributeFields;
import com.yahoo.schema.document.Attribute;
import com.yahoo.schema.document.ComplexAttributeFieldUtils;
import com.yahoo.vespa.model.application.validation.change.VespaConfigChangeAction;
import com.yahoo.vespa.model.application.validation.change.VespaRestartAction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

/**
 * Validates the changes between the current and next set of struct field attributes in a document database.
 *
 * Complex fields of the following types are considered (as they might have struct field attributes):
 *   - array of simple struct
 *   - map of simple struct
 *   - map of primitive types
 *
 * @author geirst
 */
public class StructFieldAttributeChangeValidator {

    private final ClusterSpec.Id id;
    private final NewDocumentType currentDocType;
    private final AttributeFields currentAttributes;
    private final NewDocumentType nextDocType;
    private final AttributeFields nextAttributes;

    public StructFieldAttributeChangeValidator(ClusterSpec.Id id,
                                               NewDocumentType currentDocType,
                                               AttributeFields currentAttributes,
                                               NewDocumentType nextDocType,
                                               AttributeFields nextAttributes) {
        this.id = id;
        this.currentDocType = currentDocType;
        this.currentAttributes = currentAttributes;
        this.nextDocType = nextDocType;
        this.nextAttributes = nextAttributes;
    }

    public List<VespaConfigChangeAction> validate() {
        List<VespaConfigChangeAction> result = new ArrayList<>();
        for (Field currentField : currentDocType.getAllFields()) {
            Field nextField = nextDocType.getField(currentField.getName());
            if (nextField != null) {
                result.addAll(validateAddAttributeAspect(new Context(currentField, currentAttributes),
                                                         new Context(nextField, nextAttributes)));
            }
        }
        return result;
    }

    private List<VespaConfigChangeAction> validateAddAttributeAspect(Context current, Context next) {
        return next.structFieldAttributes.stream()
                .filter(nextAttr -> current.hasFieldForStructFieldAttribute(nextAttr) &&
                                    !current.hasStructFieldAttribute(nextAttr))
                .map(nextAttr -> new VespaRestartAction(id, new ChangeMessageBuilder(nextAttr.getName()).addChange("add attribute aspect").build()))
                .collect(Collectors.toList());
    }

    private static class Context {
        public Field field;
        public Collection<Attribute> structFieldAttributes;

        public Context(Field field, AttributeFields attributes) {
            this.field = field;
            this.structFieldAttributes = attributes.structFieldAttributes(field.getName());
        }

        public DataType dataType() {
            return field.getDataType();
        }

        public boolean hasStructFieldAttribute(Attribute structFieldAttribute) {
            return structFieldAttributes.stream()
                    .anyMatch(attr -> attr.getName().equals(structFieldAttribute.getName()));
        }

        public boolean hasFieldForStructFieldAttribute(Attribute structFieldAttribute) {
            StringTokenizer fieldNames = new StringTokenizer(structFieldAttribute.getName(), ".");
            if (!fieldNames.nextToken().equals(field.getName())) {
                return false;
            }
            if (isArrayOfStructType(dataType())) {
                StructDataType nestedType = (StructDataType)((ArrayDataType)dataType()).getNestedType();
                if (structTypeContainsLastFieldNameComponent(nestedType, fieldNames)) {
                    return true;
                }
            } else if (isMapOfStructType(dataType())) {
                MapDataType mapType = (MapDataType)dataType();
                StructDataType valueType = (StructDataType)mapType.getValueType();
                String subFieldName = fieldNames.nextToken();
                if (subFieldName.equals("key") && !fieldNames.hasMoreTokens()) {
                    return true;
                } else if (subFieldName.equals("value") && structTypeContainsLastFieldNameComponent(valueType, fieldNames)) {
                    return true;
                }
            } else if (isMapOfPrimitiveType(dataType())) {
                String subFieldName = fieldNames.nextToken();
                if ((subFieldName.equals("key") || subFieldName.equals("value")) &&
                        !fieldNames.hasMoreTokens()) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isArrayOfStructType(DataType type) {
            if (type instanceof ArrayDataType) {
                ArrayDataType arrayType = (ArrayDataType)type;
                return isStructType(arrayType.getNestedType());
            } else {
                return false;
            }
        }

        private static boolean isMapOfStructType(DataType type) {
            if (type instanceof MapDataType) {
                MapDataType mapType = (MapDataType)type;
                return ComplexAttributeFieldUtils.isPrimitiveType(mapType.getKeyType()) &&
                        isStructType(mapType.getValueType());
            } else {
                return false;
            }
        }

        public static boolean isMapOfPrimitiveType(DataType type) {
            if (type instanceof MapDataType) {
                MapDataType mapType = (MapDataType)type;
                return ComplexAttributeFieldUtils.isPrimitiveType(mapType.getKeyType()) &&
                        ComplexAttributeFieldUtils.isPrimitiveType(mapType.getValueType());
            } else {
                return false;
            }
        }

        private static boolean isStructType(DataType type) {
            return (type instanceof StructDataType);
        }

        private static boolean structTypeContainsLastFieldNameComponent(StructDataType structType, StringTokenizer fieldNames) {
            return structType.getField(fieldNames.nextToken()) != null && !fieldNames.hasMoreTokens();
        }
    }

}
