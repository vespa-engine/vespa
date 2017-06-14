// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import java.util.Set;

/**
 * Interface for a basic name to IP address resolver.
 *
 * @author mpolden
 */
public interface NameResolver {

    Set<String> getAllByNameOrThrow(String hostname);

}
