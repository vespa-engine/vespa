// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.noderepository;

import com.yahoo.config.provision.TenantName;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.Collection;

/**
 * @author mortent
 * @author mpolden
 * @author smorgrav
 */
@Consumes(MediaType.APPLICATION_JSON)
public interface ProvisionResource {

    @POST
    @Path("/node")
    String addNodes(Collection<NodeRepositoryNode> node);

    @DELETE
    @Path("/node/{hostname}")
    String deleteNode(@PathParam("hostname") String hostname);

    @GET
    @Path("/node/{hostname}")
    NodeRepositoryNode getNode(@PathParam("hostname") String hostname);

    @POST
    @Path("/node/{hostname}")
    String patchNode(@PathParam("hostname") String hostname,
                     NodeRepositoryNode patchValues,
                     @HeaderParam("X-HTTP-Method-Override") String patchOverride);

    @GET
    @Path("/node/")
    NodeList listNodes(@QueryParam("recursive") boolean recursive);

    @GET
    @Path("/node/")
    NodeList listNodes(@QueryParam("application") String applicationString,
                       @QueryParam("recursive") boolean recursive);

    @GET
    @Path("/node/")
    NodeList listNodes(@QueryParam("recursive") boolean recursive,
                       @QueryParam("hostname") String hostnamesString);

    @Path("/node/")
    NodeList listNodesWithParent(@QueryParam("recursive") boolean recursive,
                                 @QueryParam("parentHost") String parentHostname);

    @GET
    @Path("/application/{application}")
    ApplicationData getApplication(@PathParam("application") String applicationId);

    @POST
    @Path("/application/{application}")
    String patchApplication(@PathParam("application") String applicationId, ApplicationPatch applicationPatch,
                   @HeaderParam("X-HTTP-Method-Override") String patchOverride);

    @PUT
    @Path("/state/{state}/{hostname}")
    String setState(@PathParam("state") NodeState state, @PathParam("hostname") String hostname);

    @POST
    @Path("/command/reboot")
    String reboot(@QueryParam("hostname") String hostname);

    @POST
    @Path("/command/restart")
    String restart(@QueryParam("hostname") String hostname);

    @GET
    @Path("/maintenance/")
    MaintenanceJobList listMaintenanceJobs();

    @POST
    @Path("/maintenance/inactive/{jobname}")
    String disableMaintenanceJob(@PathParam("jobname") String jobname);

    @DELETE
    @Path("/maintenance/inactive/{jobname}")
    String enableMaintenanceJob(@PathParam("jobname") String jobname);

    @POST
    @Path("/upgrade/{nodeType}")
    String upgrade(@PathParam("nodeType") NodeType nodeType, NodeUpgrade nodeUpgrade,
                   @HeaderParam("X-HTTP-Method-Override") String patchOverride);

    @GET
    @Path("/upgrade/")
    NodeTargetVersions upgrade();

    @POST
    @Path("/upgrade/firmware")
    String requestFirmwareChecks();

    @DELETE
    @Path("/upgrade/firmware")
    String cancelFirmwareChecks();

    @GET
    @Path("/archive")
    ArchiveList listArchives();

    @POST
    @Path("/archive/{tenant}")
    String patchArchive(@PathParam("tenant") TenantName tenant, ArchivePatch archivePatch,
                        @HeaderParam("X-HTTP-Method-Override") String patchOverride);

    @DELETE
    @Path("/archive/{tenant}")
    String removeArchiveUri(@PathParam("tenant") TenantName tenant);
}

