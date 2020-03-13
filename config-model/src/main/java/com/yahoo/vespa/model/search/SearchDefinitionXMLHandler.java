// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.vespa.model.builder.xml.dom.ModelElement;

import java.io.Serializable;
import java.util.List;

/**
 * Represents a single searchdefinition file.
 *
 * @author arnej27959
 */
public class SearchDefinitionXMLHandler implements Serializable {

    private String sdName;

    public SearchDefinitionXMLHandler(ModelElement elem) {
        sdName = elem.stringAttribute("name");
        if (sdName == null) {
            sdName = elem.stringAttribute("type");
        }
    }

    public String getName() { return sdName; }

    public SearchDefinition getResponsibleSearchDefinition(List<SearchDefinition> searchDefinitions) {
        return SearchDefinition.findByName( getName(), searchDefinitions );
    }

}
