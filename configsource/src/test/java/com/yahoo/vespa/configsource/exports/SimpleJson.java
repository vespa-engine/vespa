// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.configsource.exports;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author hakon
 */
public class SimpleJson {
    @JsonProperty("field")
    public String field;
}
