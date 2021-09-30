// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.document.annotation;

import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.document.annotation.AnnotationType;

/**
 * @author Einar M R Rosenvinge
 */
public class SDAnnotationType extends AnnotationType {

    private SDDocumentType sdDocType;
    private String inherits;

    public SDAnnotationType(String name) {
        super(name);
    }

    public SDAnnotationType(String name, SDDocumentType dataType, String inherits) {
        super(name);
        this.sdDocType = dataType;
        this.inherits = inherits;
    }

    public SDDocumentType getSdDocType() {
        return sdDocType;
    }

    public String getInherits() {
        return inherits;
    }

    public void inherit(String inherits) {
        this.inherits = inherits;
    }

}
