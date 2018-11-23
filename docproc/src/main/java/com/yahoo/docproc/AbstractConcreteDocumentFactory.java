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
    public abstract com.yahoo.document.Document getDocumentCopy(java.lang.String type, com.yahoo.document.datatypes.StructuredFieldValue src, com.yahoo.document.DocumentId id);
}
