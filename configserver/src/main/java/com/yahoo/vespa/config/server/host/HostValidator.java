// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.host;

import java.util.Collection;

/**
 * A read only host registry that has mappings from a host to some type T.
 * strings.
 *
 * @author Ulf Lilleengen
 * @since 5.9
 */
public interface HostValidator<T> {

    void verifyHosts(T key, Collection<String> newHosts);

}
