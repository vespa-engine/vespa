// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.host;

import com.yahoo.config.provision.ApplicationId;
import java.util.Collection;

/**
 * A read only host registry that has mappings from a host to application id
 *
 * @author Ulf Lilleengen
 */
public interface HostValidator {

    void verifyHosts(ApplicationId applicationId, Collection<String> newHosts);

}
