// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document.annotation;

import com.yahoo.document.annotation.AnnotationReferenceDataType;
import com.yahoo.document.annotation.AnnotationType;

/**
 * @author Einar M R Rosenvinge
 */
public class TemporaryAnnotationReferenceDataType extends AnnotationReferenceDataType {

    private final String target;

    public TemporaryAnnotationReferenceDataType(String target) {
        this.target = target;
    }

    public String getTarget() {
        return target;
    }

    @Override
    public void setAnnotationType(AnnotationType type) {
        super.setName("annotationreference<" + type.getName() + ">");
        super.setAnnotationType(type);
    }

}
