// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.schema.Schema;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;

import java.io.Serializable;
import java.util.List;

/**
 * Represents a single schema file.
 *
 * @author arnej27959
 */
public class SchemaDefinitionXMLHandler implements Serializable {

    private String schemaName;

    public SchemaDefinitionXMLHandler(ModelElement elem) {
        schemaName = elem.stringAttribute("name");
        if (schemaName == null) {
            schemaName = elem.stringAttribute("type");
        }
    }

    public String getName() { return schemaName; }

    public Schema findResponsibleSchema(List<Schema> schemas) {
        for (Schema candidate : schemas) {
            if (candidate.getName().equals(schemaName) )
                return candidate;
        }
        return null;
    }

}
