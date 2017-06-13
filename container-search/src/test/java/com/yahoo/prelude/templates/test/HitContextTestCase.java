// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.templates.test;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.Test;

import com.yahoo.prelude.templates.HitContext;
import com.yahoo.protect.ClassValidator;

/**
 * Check the entire Context class is correctly masked.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class HitContextTestCase {

    @Test
    public void checkMethods() {
        List<Method> unmasked = ClassValidator.unmaskedMethodsFromSuperclass(HitContext.class);
        assertEquals("Unmasked methods from superclass: " + unmasked, 0, unmasked.size());
    }
}
