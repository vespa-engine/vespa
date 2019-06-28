// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

import java.util.Map;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.Field;
import com.yahoo.document.annotation.Annotation;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.datatypes.StructuredFieldValue;


/**
 * Subtyped by factory classes for concrete document types. The factory classes are auto-generated
 * by vespa-documentgen-plugin. This superclass is used to manage the factories in OSGI.
 *
 * @author vegardh
 */
public abstract class AbstractConcreteDocumentFactory extends com.yahoo.component.AbstractComponent {

    public abstract Map<String, Class<? extends Document>> documentTypes();
    public abstract Map<String, Class<? extends Struct>> structTypes();
    public abstract Map<String, Class<? extends Annotation>> annotationTypes();

    /**
     * Used by the docproc framework to get an instance of a concrete document type without resorting to reflection in a bundle
     *
     * @return A concrete document instance
     */
    public abstract Document getDocumentCopy(java.lang.String type, StructuredFieldValue src, DocumentId id);

    /**
     * If the FieldValue is a StructuredFieldValue it will upgrade to the concrete type
     * @param field
     * @param fv
     * @return fv or upgraded fv
     */
    public FieldValue optionallyUpgrade(Field field, FieldValue fv) {
        if (fv instanceof StructuredFieldValue) {
            try {
                return structTypes().get(field.getDataType().getName())
                        .getConstructor(StructuredFieldValue.class)
                        .newInstance(fv);
            } catch (java.lang.Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        return fv;
    }
}
