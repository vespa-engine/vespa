// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change.search;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.document.StructDataType;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.document.Field;
import com.yahoo.vespa.model.application.validation.change.VespaConfigChangeAction;
import com.yahoo.vespa.model.application.validation.change.VespaRefeedAction;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Validates the changes between a current and next document type used in a document database.
 *
 * @author toregge
 */
public class DocumentTypeChangeValidator {

    private final ClusterSpec.Id id;
    private final NewDocumentType currentDocType;
    private final NewDocumentType nextDocType;

    private static abstract class FieldChange {

        protected final Field currentField;
        protected final Field nextField;

        public FieldChange(Field currentField, Field nextField) {
            this.currentField = currentField;
            this.nextField = nextField;
        }

        public String fieldName() {
            return currentField.getName();
        }

        public boolean valid() {
            return nextField != null;
        }

        public abstract boolean changedType();
        public abstract String currentTypeName();
        public abstract String nextTypeName();
    }

    private static class SimpleFieldChange extends FieldChange {

        public SimpleFieldChange(Field currentField, Field nextField) {
            super(currentField, nextField);
        }

        public boolean changedType() {
            return !currentField.getDataType().equals(nextField.getDataType());
        }

        public String currentTypeName() {
            return currentField.getDataType().getName();
        }

        public String nextTypeName() {
            return nextField.getDataType().getName();
        }
    }

    private static class StructFieldChange extends FieldChange {

        private final StructDataType currentType;
        private final StructDataType nextType;

        public StructFieldChange(Field currentField, Field nextField) {
            super(currentField, nextField);
            this.currentType = (StructDataType)currentField.getDataType();
            this.nextType = (StructDataType)nextField.getDataType();
        }

        public boolean changedType() {
            return changedType(currentType, nextType);
        }

        public String currentTypeName() {
            return toString(currentType);
        }

        public String nextTypeName() {
            return toString(nextType);
        }

        private static boolean changedType(StructDataType currentType, StructDataType nextType) {
            for (Field currentField : currentType.getFields()) {
                Field nextField = nextType.getField(currentField.getName());
                if (nextField != null) {
                    if (areStructFields(currentField, nextField)) {
                        if (changedType((StructDataType) currentField.getDataType(),
                                (StructDataType) nextField.getDataType())) {
                            return true;
                        }
                    } else {
                        if (!currentField.getDataType().equals(nextField.getDataType())) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        private static String toString(StructDataType dataType) {
            StringBuilder builder = new StringBuilder();
            builder.append(dataType.getName()).append(":{");
            boolean first = true;
            for (Field field : dataType.getFields()) {
                if (!first) {
                    builder.append(",");
                }
                if (field.getDataType() instanceof StructDataType) {
                    builder.append(toString((StructDataType) field.getDataType()));
                } else {
                    builder.append(field.getName() + ":" + field.getDataType().getName());
                }
                first = false;
            }
            builder.append("}");
            return builder.toString();
        }
    }

    public DocumentTypeChangeValidator(ClusterSpec.Id id,
                                       NewDocumentType currentDocType,
                                       NewDocumentType nextDocType) {
        this.id = id;
        this.currentDocType = currentDocType;
        this.nextDocType = nextDocType;
    }

    public List<VespaConfigChangeAction> validate() {
        return currentDocType.getAllFields().stream().
                map(field -> createFieldChange(field, nextDocType)).
                filter(fieldChange -> fieldChange.valid() && fieldChange.changedType()).
                map(fieldChange -> VespaRefeedAction.of(id,
                                                        ValidationId.fieldTypeChange,
                                                        new ChangeMessageBuilder(fieldChange.fieldName()).
                                                                                 addChange("data type", fieldChange.currentTypeName(),
                                                                                 fieldChange.nextTypeName()).build()
                )).
                collect(Collectors.toList());
    }

    private static FieldChange createFieldChange(Field currentField, NewDocumentType nextDocType) {
        Field nextField = nextDocType.getField(currentField.getName());
        if (nextField != null && areStructFields(currentField, nextField)) {
            return new StructFieldChange(currentField, nextField);
        }
        return new SimpleFieldChange(currentField, nextField);
    }

    private static boolean areStructFields(Field currentField, Field nextField) {
        return (currentField.getDataType() instanceof StructDataType) &&
                (nextField.getDataType() instanceof StructDataType);
    }

}
