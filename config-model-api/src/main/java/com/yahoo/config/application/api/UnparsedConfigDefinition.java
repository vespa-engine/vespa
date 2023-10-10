// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import com.yahoo.vespa.config.ConfigDefinition;

/**
 * A config definition that has not been parsed.
 * 
 * @author Ulf Lilleengen
*/
public interface UnparsedConfigDefinition {

    ConfigDefinition parse();
    String getUnparsedContent();

}
