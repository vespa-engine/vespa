// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom.chains;

import com.yahoo.component.chain.dependencies.Dependencies;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Basic tests of DependencyBuilder
 * @author Tony Vaagenes
 */
public class DependenciesBuilderTest extends DomBuilderTest {
    private Set<String> set(String str) {
        Set<String> symbols = new HashSet<>();
        for (String symbol : str.split(",")) {
            symbols.add(symbol);
        }
        return symbols;
    }

    @Test
    void testBuildDependencies() {
        DependenciesBuilder dependenciesBuilder = new DependenciesBuilder(parse(
                "<searcher provides='symbol1  symbol2 ' before='p1' after=' s1' >",
                "  <provides> symbol3 </provides>",
                "  <provides> symbol4 </provides>",
                "  <before> p2 </before>",
                "  <after>s2</after>",
                "</searcher>"));

        Dependencies dependencies = dependenciesBuilder.build();

        assertEquals(dependencies.provides(),
                set("symbol1,symbol2,symbol3,symbol4"));

        assertEquals(dependencies.before(),
                set("p1,p2"));

        assertEquals(dependencies.after(),
                set("s1,s2"));
    }
}
