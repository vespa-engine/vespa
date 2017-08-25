// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.rotation;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.rotation.Rotation;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.Set;

/**
 * A rotation repository assigns global rotations to Vespa applications. It does not take into account
 * whether an application qualifies or not, but it assumes that each application should get only
 * one.
 *
 * The list of rotations comes from the RotationsConfig, set in the controller's services.xml.
 * Assignments are persisted with the RotationId as the primary key. When we assign the
 * rotation to an application we try to put the mapping RotationId -&gt; Application.  If a
 * mapping already exists for that RotationId, the assignment will fail.
 *
 * @author Oyvind Gronnesby
 */
public interface RotationRepository {

    // TODO: Change to use provision.ApplicationId
    // TODO: Move the persistence into ControllerDb (done), and then collapse the 2 implementations and the interface into one
    
    /**
     * If any rotations are assigned to the application, these will be returned.
     * If no rotations are assigned, assign one rotation to the application and return that.
     * 
     * @param applicationId ID of the application to get or assign rotation for
     * @param deploymentSpec Spec of current application being deployed
     * @return Set of rotations assigned (may be empty)
     */
    @NotNull
    Set<Rotation> getOrAssignRotation(ApplicationId applicationId, DeploymentSpec deploymentSpec);

    /**
     * Get the external visible rotation URIs for this application.
     * 
     * @param applicationId ID of the application to get or assign rotation for
     */
    @NotNull
    Set<URI> getRotationUris(ApplicationId applicationId);

}
