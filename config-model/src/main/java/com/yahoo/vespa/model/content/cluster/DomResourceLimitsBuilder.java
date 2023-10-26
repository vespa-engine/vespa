// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.cluster;

import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.content.ResourceLimits;

/**
 * Builder for feed block resource limits.
 *
 * @author geirst
 */
public class DomResourceLimitsBuilder {

    public static ResourceLimits.Builder createBuilder(ModelElement contentXml, boolean hostedVespa) {
        ResourceLimits.Builder builder = new ResourceLimits.Builder();
        ModelElement resourceLimits = contentXml.child("resource-limits");
        if (resourceLimits == null) { return builder; }

        if (hostedVespa)
            throw new IllegalArgumentException("Element '" + resourceLimits + "' is not allowed to be set");

        if (resourceLimits.child("disk") != null) {
            builder.setDiskLimit(resourceLimits.childAsDouble("disk"));
        }
        if (resourceLimits.child("memory") != null) {
            builder.setMemoryLimit(resourceLimits.childAsDouble("memory"));
        }
        return builder;
    }

}
