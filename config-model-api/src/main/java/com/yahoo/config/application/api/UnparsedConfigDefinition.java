// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import com.yahoo.vespa.config.ConfigDefinition;

/**
 * Represents a config definition that has not been parsed.
 * @author lulf
 * @since 5.20
*/
public interface UnparsedConfigDefinition {
    public ConfigDefinition parse();
    public String getUnparsedContent();
}
