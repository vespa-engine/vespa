package com.yahoo.vespa.model.application.validation.change.search;

import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.searchdefinition.derived.AttributeFields;
import com.yahoo.searchdefinition.document.Attribute;
import com.yahoo.vespa.model.application.validation.change.VespaConfigChangeAction;
import com.yahoo.vespa.model.application.validation.change.VespaRefeedAction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import static com.yahoo.searchdefinition.document.ComplexAttributeFieldUtils.isArrayOfSimpleStruct;
import static com.yahoo.searchdefinition.document.ComplexAttributeFieldUtils.isMapOfPrimitiveType;
import static com.yahoo.searchdefinition.document.ComplexAttributeFieldUtils.isMapOfSimpleStruct;

/**
 * Validates the changes between the current and next set of struct field attributes in a document database.

 * Complex fields of the following types are considered (as they might have struct field attributes):
 *   - array of simple struct
 *   - map of simple struct
 *   - map of primitive types
 *
 * @author geirst
 */
public class StructFieldAttributeChangeValidator {

    private final NewDocumentType currentDocType;
    private final AttributeFields currentAttributes;
    private final NewDocumentType nextDocType;
    private final AttributeFields nextAttributes;

    public StructFieldAttributeChangeValidator(NewDocumentType currentDocType,
                                               AttributeFields currentAttributes,
                                               NewDocumentType nextDocType,
                                               AttributeFields nextAttributes) {
        this.currentDocType = currentDocType;
        this.currentAttributes = currentAttributes;
        this.nextDocType = nextDocType;
        this.nextAttributes = nextAttributes;
    }

    public List<VespaConfigChangeAction> validate(ValidationOverrides overrides, Instant now) {
        List<VespaConfigChangeAction> result = new ArrayList();
        for (Field currentField : currentDocType.getAllFields()) {
            Field nextField = nextDocType.getField(currentField.getName());
            if (nextField != null) {
                result.addAll(validateAddAttributeAspect(new Context(currentField, currentAttributes),
                        new Context(nextField, nextAttributes),
                        overrides, now));
            }
        }
        return result;
    }

    private List<VespaConfigChangeAction> validateAddAttributeAspect(Context current, Context next, ValidationOverrides overrides, Instant now) {
        return next.structFieldAttributes.stream()
                .filter(nextAttr -> current.hasFieldFor(nextAttr) &&
                        !current.hasStructFieldAttribute(nextAttr))
                .map(nextAttr -> VespaRefeedAction.of("field-type-change",
                        overrides,
                        new ChangeMessageBuilder(nextAttr.getName())
                                .addChange("add attribute aspect").build(),
                        now))
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

        public boolean hasFieldFor(Attribute structFieldAttribute) {
            StringTokenizer fieldNames = new StringTokenizer(structFieldAttribute.getName(), ".");
            if (!fieldNames.nextToken().equals(field.getName())) {
                return false;
            }
            if (isArrayOfSimpleStruct(dataType())) {
                StructDataType nestedType = (StructDataType)((ArrayDataType)dataType()).getNestedType();
                if (hasLastFieldInStructType(fieldNames, nestedType)) {
                    return true;
                }
            } else if (isMapOfSimpleStruct(dataType())) {
                MapDataType mapType = (MapDataType)dataType();
                StructDataType valueType = (StructDataType)mapType.getValueType();
                String subFieldName = fieldNames.nextToken();
                if (subFieldName.equals("key") && !fieldNames.hasMoreTokens()) {
                    return true;
                } else if (subFieldName.equals("value") && hasLastFieldInStructType(fieldNames, valueType)) {
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

        private static boolean hasLastFieldInStructType(StringTokenizer fieldNames, StructDataType structType) {
            return structType.getField(fieldNames.nextToken()) != null && !fieldNames.hasMoreTokens();
        }

    }

}
