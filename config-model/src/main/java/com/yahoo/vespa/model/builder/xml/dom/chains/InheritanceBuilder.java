// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.config.model.builder.xml.XmlHelper;
import com.yahoo.text.XML;
import org.w3c.dom.Element;

import java.util.*;

/**
 * Build an Inheritance object from an inheritance section.
 * @author Tony Vaagenes
 */
public class InheritanceBuilder {
    final ChainSpecification.Inheritance inheritance;

    public InheritanceBuilder(Element spec) {
        inheritance = new ChainSpecification.Inheritance(
                // XXX: for this to work, the tagname in the spec must match the tagname inside the 'inherits' elem, e.g. 'searchchain->inherits->searchchain'
                read(spec, "inherits", spec.getTagName()),
                read(spec, "excludes", "exclude"));
    }

    public ChainSpecification.Inheritance build() {
        return inheritance;
    }

    private Set<ComponentSpecification> read(Element spec, String attributeName, String elementName) {
        Set<ComponentSpecification> componentSpecifications = new LinkedHashSet<>();

        componentSpecifications.addAll(spaceSeparatedComponentSpecificationsFromAttribute(spec, attributeName));

        // TODO: the 'inherits' element is undocumented, and can be removed in an upcoming version of Vespa
        componentSpecifications.addAll(idRefFromElements(XML.getChild(spec, "inherits"), elementName));


        return componentSpecifications;
    }

    private Collection<ComponentSpecification> idRefFromElements(Element spec, String elementName) {
        Collection<ComponentSpecification> result = new ArrayList<>();
        if (spec == null)
            return result;

        for (Element element : XML.getChildren(spec, elementName)) {
            result.add(XmlHelper.getIdRef(element));
        }
        return result;
    }

    private Collection<ComponentSpecification> spaceSeparatedComponentSpecificationsFromAttribute(Element spec, String attributeName) {
        return toComponentSpecifications(XmlHelper.spaceSeparatedSymbolsFromAttribute(spec, attributeName));
    }

    private Set<ComponentSpecification> toComponentSpecifications(Collection<String> symbols) {
        Set<ComponentSpecification> specifications = new LinkedHashSet<>();
        for (String symbol : symbols) {
            specifications.add(new ComponentSpecification(symbol));
        }
        return specifications;
    }
}
