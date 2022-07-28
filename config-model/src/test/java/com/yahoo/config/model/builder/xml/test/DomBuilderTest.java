// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.builder.xml.test;

import com.yahoo.config.model.test.TestUtil;
import org.junit.jupiter.api.BeforeEach;
import com.yahoo.config.model.test.MockRoot;
import org.w3c.dom.Element;

/**
 * Utility functions for testing dom builders.
 *
 * For an example,
 * @see com.yahoo.vespa.model.builder.xml.dom.chains.DependenciesBuilderTest
 *
 * @author Tony Vaagenes
 */
abstract public class DomBuilderTest {

    public static Element parse(String... xmlLines) {
        return TestUtil.parse(xmlLines);
    }

    protected MockRoot root;

    @BeforeEach
    public void setup() {
        root = new MockRoot();
    }
}
