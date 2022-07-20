// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;

import com.yahoo.security.tls.Capability;
import com.yahoo.security.tls.CapabilityMode;
import com.yahoo.security.tls.CapabilitySet;
import com.yahoo.security.tls.ConnectionAuthContext;
import com.yahoo.security.tls.TransportSecurityUtils;

import java.util.logging.Logger;

import static com.yahoo.security.tls.CapabilityMode.DISABLE;
import static com.yahoo.security.tls.CapabilityMode.LOG_ONLY;

/**
 * @author bjorncs
 */
public class RequireCapabilitiesFilter implements RequestAccessFilter {

    private static final Logger log = Logger.getLogger(RequireCapabilitiesFilter.class.getName());
    private static final CapabilityMode MODE = TransportSecurityUtils.getCapabilityMode();

    private final CapabilitySet requiredCapabilities;

    public RequireCapabilitiesFilter(CapabilitySet requiredCapabilities) {
        this.requiredCapabilities = requiredCapabilities;
    }

    public RequireCapabilitiesFilter(Capability... requiredCapabilities) {
        this(CapabilitySet.from(requiredCapabilities));
    }

    @Override
    public boolean allow(Request r) {
        if (MODE == DISABLE) return true;
        ConnectionAuthContext ctx = r.target().connectionAuthContext();
        CapabilitySet peerCapabilities = ctx.capabilities();
        boolean authorized = peerCapabilities.has(requiredCapabilities);
        if (!authorized) {
            String msg = "%sPermission denied for RPC method '%s'. Peer at %s with %s. Call requires %s, but peer has %s"
                    .formatted(MODE == LOG_ONLY ? "Dry-run: " : "", r.methodName(), r.target().peerSpec(), ctx.peerCertificateString().orElseThrow(),
                            requiredCapabilities.toNames(), peerCapabilities.toNames());
            if (MODE == LOG_ONLY) {
                log.info(msg);
                return true;
            } else {
                log.warning(msg);
                return false;
            }
        }
        return true;
    }

}
