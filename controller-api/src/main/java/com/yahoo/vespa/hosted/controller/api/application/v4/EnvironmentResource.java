// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.application.v4;

import com.fasterxml.jackson.databind.JsonNode;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeployResult;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.InstanceInformation;
import com.yahoo.vespa.hosted.controller.api.identifiers.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.identifiers.EnvironmentId;
import com.yahoo.vespa.hosted.controller.api.identifiers.Hostname;
import com.yahoo.vespa.hosted.controller.api.identifiers.InstanceId;
import com.yahoo.vespa.hosted.controller.api.identifiers.RegionId;
import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.io.InputStream;

/**
 * @author Tony Vaagenes
 * @author gv
 */
@Path("") //Ensures that the produces annotation is inherited
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface EnvironmentResource {

    String API_PATH = "environment";

    String APPLICATION_ZIP = "applicationZip";
    String DEPLOY_OPTIONS = "deployOptions";

    @POST
    @Path("{environmentId}/region/{regionId}/instance/{instanceId}/deploy")
    @Consumes({MediaType.MULTIPART_FORM_DATA})
    DeployResult deploy(@PathParam("tenantId") TenantId tenantId,
                        @PathParam("applicationId") ApplicationId applicationId,
                        @PathParam("environmentId") EnvironmentId environmentId,
                        @PathParam("regionId") RegionId regionId,
                        @PathParam("instanceId") InstanceId instanceId,
                        @FormDataParam(APPLICATION_ZIP) InputStream applicationZipFile,
                        @FormDataParam(APPLICATION_ZIP) FormDataContentDisposition fileMetaData,
                        @FormDataParam(DEPLOY_OPTIONS) FormDataBodyPart deployOptions);

    @DELETE
    @Path("{environmentId}/region/{regionId}/instance/{instanceId}")
    String deactivate(@PathParam("tenantId") TenantId tenantId,
                      @PathParam("applicationId") ApplicationId applicationId,
                      @PathParam("environmentId") EnvironmentId environmentId,
                      @PathParam("regionId") RegionId regionId,
                      @PathParam("instanceId") InstanceId instanceId);

    @POST
    @Path("{environmentId}/region/{regionId}/instance/{instanceId}/restart")
    String restart(@PathParam("tenantId") TenantId tenantId,
                   @PathParam("applicationId") ApplicationId applicationId,
                   @PathParam("environmentId") EnvironmentId environmentId,
                   @PathParam("regionId") RegionId regionId,
                   @PathParam("instanceId") InstanceId instanceId,
                   @QueryParam("hostname") Hostname hostname);

    @GET
    @Path("{environmentId}/region/{regionId}/instance/{instanceId}")
    InstanceInformation instanceInfo(@PathParam("tenantId") TenantId tenantId,
                                     @PathParam("applicationId") ApplicationId applicationId,
                                     @PathParam("environmentId") EnvironmentId environmentId,
                                     @PathParam("regionId") RegionId regionId,
                                     @PathParam("instanceId") InstanceId instanceId);

    @GET
    @Path("{environmentId}/region/{regionId}/instance/{instanceId}/converge")
    JsonNode waitForConfigConverge(@PathParam("tenantId") TenantId tenantId,
                                   @PathParam("applicationId") ApplicationId applicationId,
                                   @PathParam("environmentId") EnvironmentId environmentId,
                                   @PathParam("regionId") RegionId regionId,
                                   @PathParam("instanceId") InstanceId instanceId,
                                   @QueryParam("timeout") long timeoutInSeconds);

    @Path("{environmentId}/region/{regionId}/instance/{instanceId}/service")
    ServiceViewResource service();
}
