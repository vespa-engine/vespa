// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.serviceview.bindings;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * View of {@link com.yahoo.cloud.config.ModelConfig}.
 *
 * @author mortent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModelResponse {
    public List<HostService> hosts;
    public String vespaVersion;
}
