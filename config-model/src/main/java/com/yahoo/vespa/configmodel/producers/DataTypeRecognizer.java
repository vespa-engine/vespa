// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.configmodel.producers;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.MapDataType;
import com.yahoo.document.NumericDataType;
import com.yahoo.document.PositionDataType;
import com.yahoo.document.PrimitiveDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.document.annotation.AnnotationType;
import com.yahoo.document.annotation.AnnotationReferenceDataType;
import com.yahoo.documentmodel.DataTypeCollection;
import com.yahoo.documentmodel.NewDocumentReferenceDataType;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.documentmodel.OwnedStructDataType;
import com.yahoo.documentmodel.VespaDocumentType;
import com.yahoo.schema.document.annotation.SDAnnotationType;

import java.util.*;

/**
 * Class to produce unique names for DataType instances that have
 * different contents even when their getName() and getId() may be
 * equal.
 *
 * @author arnej
 **/
public class DataTypeRecognizer {
    private Map<Object, String> toUniqueNames = new IdentityHashMap<>();

    DataTypeRecognizer() {
    }

    DataTypeRecognizer(DataTypeCollection dtc) {
        for (var type : dtc.getTypes()) {
            System.err.println("added: "+nameOf(type));
        }
    }

    String nameOf(Object type) {
        return toUniqueNames.computeIfAbsent(type, t -> makeUniqueName(t));
    }

    private String makeUniqueName(Object type) {
        if (type == PositionDataType.INSTANCE) {
            return "{builtin position}";
        }
        if (type == DataType.DOCUMENT) {
            return "{builtin root document}";
        }
        if (type == VespaDocumentType.INSTANCE) {
            return "{builtin vespa document}";
        }
        var typeClass = type.getClass();
        if (typeClass == AnnotationReferenceDataType.class) {
            var t = (AnnotationReferenceDataType) type;
            var ann = t.getAnnotationType();
            return "annotationreference<" + ann.getName() + ">";
        }
        if (typeClass == AnnotationType.class) {
            var t = (AnnotationType) type;
            return "annotation<" + t.getName() + ">";
        }
        if (typeClass == SDAnnotationType.class) {
            var t = (SDAnnotationType) type;
            return "annotation<" + t.getName() + ">";
        }
        if (typeClass == ArrayDataType.class) {
            var t = (ArrayDataType) type;
            var nt = t.getNestedType();
            return "array<" + nameOf(nt) + ">";
        }
        if (typeClass == DocumentType.class) {
            var t = (DocumentType) type;
            return "{document "+t.getName()+"}";
        }
        if (typeClass == MapDataType.class) {
            var t = (MapDataType) type;
            var kt = t.getKeyType();
            var vt = t.getValueType();
            return "map<" + nameOf(kt) + ", " + nameOf(vt) + ">";
        }
        if (typeClass == NewDocumentReferenceDataType.class) {
            var t = (NewDocumentReferenceDataType) type;
            return "reference<" + t.getTargetTypeName() + ">";
        }
        if (typeClass == NewDocumentType.class) {
            var t = (NewDocumentType) type;
            return "{new-document "+t.getName()+"}";
        }
        if (typeClass == OwnedStructDataType.class) {
            var t = (OwnedStructDataType) type;
            return "{owned-struct " + t.getName() + " @ " + t.getOwnerName() + "}";
        }
        if (typeClass == NumericDataType.class) {
            var t = (NumericDataType) type;
            return "{numeric " + t.getName() + "}";
        }
        if (typeClass == PrimitiveDataType.class) {
            var t = (PrimitiveDataType) type;
            return "{primitive " + t.getName() + "}";
        }
        if (typeClass == StructDataType.class) {
            var t = (StructDataType) type;
            return "{struct "+t.getName()+"}";
        }
        if (typeClass == TensorDataType.class) {
            var t = (TensorDataType) type;
            return "{tensor" + t.getTensorType() + "}";
        }
        if (typeClass == WeightedSetDataType.class) {
            var t = (WeightedSetDataType) type;
            var nt = t.getNestedType();
            String prefix = "weightedset<";
            String cine = t.createIfNonExistent() ? " [createIfNonExistent]" : "";
            String riz = t.removeIfZero() ? " [removeIfZero]" : "";
            String suffix = ">";
            return prefix + nameOf(nt) + cine + riz + suffix;
        }
        throw new IllegalArgumentException("unknown type class: "+typeClass);
    }

}
