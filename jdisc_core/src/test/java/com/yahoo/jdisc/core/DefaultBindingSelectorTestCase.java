// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.google.inject.Guice;
import com.yahoo.jdisc.application.BindingSet;
import com.yahoo.jdisc.application.BindingSetSelector;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Simon Thoresen Hult
 */
public class DefaultBindingSelectorTestCase {

    @Test
    void requireThatClassIsInjectedByDefault() {
        BindingSetSelector selector = Guice.createInjector().getInstance(BindingSetSelector.class);
        assertTrue(selector instanceof DefaultBindingSelector);
    }

    @Test
    void requireThatDefaultSetIsAlwaysSelected() {
        DefaultBindingSelector selector = new DefaultBindingSelector();
        assertEquals(BindingSet.DEFAULT, selector.select(null));
        for (int i = 0; i < 69; ++i) {
            assertEquals(BindingSet.DEFAULT, selector.select(newUri()));
        }
    }

    private static URI newUri() {
        return URI.create("foo" + System.nanoTime() + "://bar" + System.nanoTime() + "/baz" + System.nanoTime());
    }
}
