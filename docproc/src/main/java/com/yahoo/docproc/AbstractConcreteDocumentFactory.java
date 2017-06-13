// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.annotation.Annotation;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.datatypes.StructuredFieldValue;
import com.yahoo.yolean.Exceptions;

/**
 * Subtyped by factory classes for concrete document types. The factory classes are auto-generated
 * by vespa-documentgen-plugin. This superclass is used to manage the factories in OSGI.
 * @author vegardh
 * @since 5.1
 *
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
    public com.yahoo.document.Document getDocumentCopy(java.lang.String type, com.yahoo.document.datatypes.StructuredFieldValue src, com.yahoo.document.DocumentId id) {
        // Note: This method can't be abstract because it must work with older bundles where the ConcreteDocumentFactory may not implement it.
        // It is overridden to not use reflection by newer bundles. 
        // The implementation here is not so good in bundles, since it instantiates the doc using reflection.
        // TODO: for 6.0: make this method abstract and throw away the code below.
        Class<? extends Document> concreteClass = documentTypes().get(type);
        try {
            Constructor<? extends Document> copyCon = concreteClass.getConstructor(StructuredFieldValue.class, DocumentId.class);
            return copyCon.newInstance(src, id);
        } catch (InvocationTargetException | NoSuchMethodException | InstantiationException | IllegalAccessException e) {            
            throw new RuntimeException(Exceptions.toMessageString(e), e);
        }
    }
}
