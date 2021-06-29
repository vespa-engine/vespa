// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.container;

/**
 * Interface for retrieving credentials for a container registry.
 *
 * @author mpolden
 */
public interface RegistryCredentialsProvider {

    RegistryCredentials get();

}
