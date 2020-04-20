// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.vespa.model.builder.xml.dom.ModelElement;

import java.io.Serializable;
import java.util.List;

/**
 * Represents a single schema file.
 *
 * @author arnej27959
 */
public class SchemaDefinitionXMLHandler implements Serializable {

    private String sdName;

    public SchemaDefinitionXMLHandler(ModelElement elem) {
        sdName = elem.stringAttribute("name");
        if (sdName == null) {
            sdName = elem.stringAttribute("type");
        }
    }

    public String getName() { return sdName; }

    public NamedSchema getResponsibleSearchDefinition(List<NamedSchema> schemas) {
        return NamedSchema.findByName(getName(), schemas );
    }

}
