// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.foo.SimpletypesConfig;
import org.junit.Test;

import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 */
public class ConfigSetTest {

    @Test
    public void testToString() {
        ConfigSet set = new ConfigSet();
        SimpletypesConfig.Builder builder = new SimpletypesConfig.Builder();
        set.addBuilder("foo", builder);
        assertTrue(Pattern.matches("name=foo.simpletypes,configId=foo=>com.yahoo.foo.SimpletypesConfig.*",
                set.toString()));
    }
}
