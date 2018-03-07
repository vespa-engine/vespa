// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.configserver.noderepository.bindings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.yahoo.vespa.hosted.node.admin.nodeagent.NodeAttributes;

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
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateNodeAttributesRequestBody {
    public Long currentRestartGeneration;
    public Long currentRebootGeneration;
    public String currentDockerImage;
    public String currentVespaVersion;
    public String hardwareDivergence;

    public UpdateNodeAttributesRequestBody(NodeAttributes nodeAttributes) {
        if (nodeAttributes.getDockerImage() != null) {
            this.currentDockerImage = nodeAttributes.getDockerImage().asString();
        }

        this.currentRestartGeneration = nodeAttributes.getRestartGeneration();
        this.currentVespaVersion = nodeAttributes.getVespaVersion();
        this.currentRebootGeneration = nodeAttributes.getRebootGeneration();
        this.hardwareDivergence = nodeAttributes.getHardwareDivergence();
    }
}
