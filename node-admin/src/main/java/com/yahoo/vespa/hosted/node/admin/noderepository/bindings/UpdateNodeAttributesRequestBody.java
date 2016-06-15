// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.noderepository.bindings;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Automagically handles (de)serialization based on 1:1 message fields and identifier names.
 * Instances of this class should serialize as:
 * <pre>
 *   {
 *     "currentRestartGeneration": 42
 *   }
 * </pre>
 *
 * @author bakksjo
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateNodeAttributesRequestBody {
    public Long currentRestartGeneration;
    public String currentDockerImage;
    public String currentVespaVersion;

    public UpdateNodeAttributesRequestBody(
            final Long restartGeneration,
            final String currentDockerImage,
            String currentVespaVersion) {
        this.currentRestartGeneration = restartGeneration;
        this.currentDockerImage = currentDockerImage;
        this.currentVespaVersion = currentVespaVersion;
    }
}
