// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.docker;

import com.yahoo.vespa.hosted.dockerapi.RegistryCredentials;

/**
 * Interface for retrieving credentials for a container registry.
 *
 * @author mpolden
 */
public interface RegistryCredentialsProvider {

    RegistryCredentials get();

}
