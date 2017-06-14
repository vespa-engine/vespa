// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

/**
 * Interface to hide dependency on prelude from application package module due to semantic rules
 * rewriting.
 *
 * @author lulf
 * @since 5.22
 */
// TODO: This is not used any more. Do a phased removal while keeping config model compatibility
public interface RuleConfigDeriver {
    void derive(String ruleBaseDir, String outputDir) throws Exception;
}
