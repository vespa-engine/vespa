// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.cluster;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.content.ResourceLimits;

import java.util.logging.Level;

/**
 * Builder for feed block resource limits.
 *
 * @author geirst
 */
public class DomResourceLimitsBuilder {

    public static ResourceLimits.Builder createBuilder(ModelElement contentXml, boolean hostedVespa, DeployLogger deployLogger) {
        ResourceLimits.Builder builder = new ResourceLimits.Builder();
        ModelElement resourceLimits = contentXml.child("resource-limits");
        if (resourceLimits == null) { return builder; }

        if (hostedVespa) {
            deployLogger.logApplicationPackage(Level.WARNING, "Element " + resourceLimits +
                                                              " is not allowed, default limits will be used");
            // TODO: Throw exception when we are sure nobody is using this
            //throw new IllegalArgumentException("Element " + element + " is not allowed to be set, default limits will be used");
            return builder;
        }
        if (resourceLimits.child("disk") != null) {
            builder.setDiskLimit(resourceLimits.childAsDouble("disk"));
        }
        if (resourceLimits.child("memory") != null) {
            builder.setMemoryLimit(resourceLimits.childAsDouble("memory"));
        }
        return builder;
    }

}
