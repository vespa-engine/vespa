// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.configtest;

/**
 * Just check we manage to compile something that requires configuration and
 * has no dependencies to Vespa.
 */
public class Demo {
    public final String greeting;

    public Demo(GreetingConfig config) {
        greeting = config.greeting();
    }
}
