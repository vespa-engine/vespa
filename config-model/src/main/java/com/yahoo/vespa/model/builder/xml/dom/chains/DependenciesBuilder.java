// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains;

import com.yahoo.component.chain.dependencies.Dependencies;
import com.yahoo.config.model.builder.xml.XmlHelper;
import org.w3c.dom.Element;

import java.util.HashSet;
import java.util.Set;

/**
 * Builds Dependencies (provides, before, after) from an element.
 * @author Tony Vaagenes
 */
public class DependenciesBuilder {
    private final Dependencies dependencies;

    public DependenciesBuilder(Element spec) {

        Set<String> provides = read(spec, "provides");
        Set<String> before = read(spec, "before");
        Set<String> after = read(spec, "after");

        dependencies = new Dependencies(provides, before, after);
    }

    public Dependencies build() {
        return dependencies;
    }

    private Set<String> read(Element spec, String name) {
        Set<String> symbols = new HashSet<>();
        symbols.addAll(XmlHelper.valuesFromElements(spec, name));
        symbols.addAll(XmlHelper.spaceSeparatedSymbolsFromAttribute(spec, name));

        return symbols;
    }
}
