// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.cluster;

import com.yahoo.config.model.api.IgnorableExceptionId;
import com.yahoo.config.model.api.IgnorableIllegalArgumentException;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.content.ResourceLimits;

/**
 * Builder for feed block resource limits.
 *
 * @author geirst
 */
public class DomResourceLimitsBuilder {

    public static final IgnorableExceptionId illegalArgumentId = new IgnorableExceptionId("resource-limits");

    public static ResourceLimits.Builder createBuilder(ModelElement contentXml,
                                                       boolean hostedVespa,
                                                       IgnorableExceptionId ignorableExceptionId) {
        ResourceLimits.Builder builder = new ResourceLimits.Builder();
        ModelElement resourceLimits = contentXml.child("resource-limits");
        if (resourceLimits == null) { return builder; }

        if (hostedVespa) {
            // This exception is explicitly ignorable, so just return with default values (logging happens where id is created)
            if (ignorableExceptionId.equals(illegalArgumentId)) return builder;

            throw new IgnorableIllegalArgumentException("Element '" + resourceLimits + "' is not allowed in services.xml",
                                                        illegalArgumentId);
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
