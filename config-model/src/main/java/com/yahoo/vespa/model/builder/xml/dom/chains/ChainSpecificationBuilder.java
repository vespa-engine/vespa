// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.Phase;
import com.yahoo.component.chain.dependencies.Dependencies;
import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.text.XML;
import com.yahoo.config.model.builder.xml.XmlHelper;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;


/**
 * Creates a partial ChainSpecification without inner components
 * @author Tony Vaagenes
 */
public class ChainSpecificationBuilder {
    private final ComponentId componentId;
    private final ChainSpecification.Inheritance inheritance;
    private final Collection<Phase> phases;

    public ChainSpecificationBuilder(Element chainElement) {
        componentId = readComponentId(chainElement);
        inheritance = readInheritance(chainElement);
        phases = readPhases(chainElement);
    }

    private Set<Phase> readPhases(Element parentElement) {
        Set<Phase> phases = new LinkedHashSet<>();

        for (Element phaseSpec : XML.getChildren(parentElement, "phase")) {
            String name = XmlHelper.getIdString(phaseSpec);
            Dependencies dependencies = new DependenciesBuilder(phaseSpec).build();
            phases.add(new Phase(name, dependencies));
        }
        return phases;
    }

    private ComponentId readComponentId(Element spec) {
        return XmlHelper.getId(spec);
    }

    private ChainSpecification.Inheritance readInheritance(Element spec) {
        return new InheritanceBuilder(spec).build();
    }

    public ChainSpecification build(Set<ComponentSpecification> outerComponentReferences) {
        return new ChainSpecification(componentId, inheritance, phases, outerComponentReferences);
    }
}
