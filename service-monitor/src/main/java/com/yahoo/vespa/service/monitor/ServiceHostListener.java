// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.service.monitor;

import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;

import java.util.Set;

/**
 * Interface for listening to changes to the set of applications, or the set of hosts
 * assigned to each application, in the service model.
 *
 * <p>This is equivalent to listening to the duper model, since no health information leaks through
 * from the service model, but the exposed types are those of the service model.</p>
 */
public interface ServiceHostListener {
    void onApplicationActivate(ApplicationInstanceReference reference, Set<HostName> hostnames);
    void onApplicationRemove(ApplicationInstanceReference reference);
}
