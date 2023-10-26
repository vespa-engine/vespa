// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

/**
 * Indicates that a config definition (typically a def file schema) was unknown to the config server
 * 
 * @author Ulf Lilleengen
 */
public class UnknownConfigDefinitionException extends IllegalArgumentException {
    public UnknownConfigDefinitionException(String s) {
        super(s);
    }
}
