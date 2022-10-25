// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.config.model.builder.xml.XmlHelper;
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Build an Inheritance object from an inheritance section.
 * @author Tony Vaagenes
 */
public class InheritanceBuilder {
    final ChainSpecification.Inheritance inheritance;

    public InheritanceBuilder(Element spec) {
        inheritance = new ChainSpecification.Inheritance(
                read(spec, "inherits"),
                read(spec, "excludes"));
    }

    public ChainSpecification.Inheritance build() {
        return inheritance;
    }

    private Set<ComponentSpecification> read(Element spec, String attributeName) {
        Set<ComponentSpecification> componentSpecifications = new LinkedHashSet<>();

        componentSpecifications.addAll(spaceSeparatedComponentSpecificationsFromAttribute(spec, attributeName));

        return componentSpecifications;
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
