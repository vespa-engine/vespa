// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.container;


import java.util.List;

/**
 * A registry of container images.
 *
 * @author mpolden
 */
public interface ContainerRegistry {

    /** Delete all given images */
    void deleteAll(List<ContainerImage> images);

    /** Returns a list of all container images in this system */
    List<ContainerImage> list();

}
