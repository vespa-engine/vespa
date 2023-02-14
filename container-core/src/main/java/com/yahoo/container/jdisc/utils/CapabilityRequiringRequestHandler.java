// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.jdisc.utils;

import com.yahoo.container.jdisc.RequestView;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.security.tls.Capability;
import com.yahoo.security.tls.CapabilitySet;

/**
 * @author bjorncs
 */
public interface CapabilityRequiringRequestHandler extends RequestHandler {
    Capability DEFAULT_REQUIRED_CAPABILITY = Capability.HTTP_UNCLASSIFIED;

    default CapabilitySet requiredCapabilities(RequestView req) { return requiredCapability(req).toCapabilitySet(); }
    default Capability requiredCapability(RequestView req) { return DEFAULT_REQUIRED_CAPABILITY; }

}
