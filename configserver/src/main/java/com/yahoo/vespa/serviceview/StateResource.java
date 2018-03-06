// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.serviceview;

import com.yahoo.container.jaxrs.annotation.Component;
import com.yahoo.vespa.serviceview.bindings.ApplicationView;
import com.yahoo.vespa.serviceview.bindings.ConfigClient;
import com.yahoo.vespa.serviceview.bindings.HealthClient;
import com.yahoo.vespa.serviceview.bindings.ModelResponse;
import com.yahoo.vespa.serviceview.bindings.StateClient;
import org.glassfish.jersey.client.proxy.WebResourceFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;


/**
 * A web service to discover and proxy Vespa service state info.
 *
 * @author Steinar Knutsen
 */
@Path("/")
public class StateResource implements StateClient {

    private static final String SINGLE_API_LINK = "url";

    private final int restApiPort;
    private final String host;
    private final UriInfo uriInfo;

    @SuppressWarnings("serial")
    private static class GiveUpLinkRetargetingException extends Exception {
        public GiveUpLinkRetargetingException(Throwable reason) {
            super(reason);
        }

        public GiveUpLinkRetargetingException(String message) {
            super(message);
        }
    }

    public StateResource(@Component ConfigServerLocation configServer, @Context UriInfo ui) {
        this.restApiPort = configServer.restApiPort;
        this.host = "localhost";
        this.uriInfo = ui;
    }

    @Override
    @GET
    @Path("v1/")
    @Produces(MediaType.APPLICATION_JSON)
    public ApplicationView getDefaultUserInfo() {
        return getUserInfo("default", "default", "default", "default", "default");
    }

    @Override
    @GET
    @Path("v1/tenant/{tenantName}/application/{applicationName}/environment/{environmentName}/region/{regionName}/instance/{instanceName}")
    @Produces(MediaType.APPLICATION_JSON)
    public ApplicationView getUserInfo(@PathParam("tenantName") String tenantName,
                                @PathParam("applicationName") String applicationName,
                                @PathParam("environmentName") String environmentName,
                                @PathParam("regionName") String regionName,
                                @PathParam("instanceName") String instanceName) {
        ServiceModel model = new ServiceModel(
                getModelConfig(tenantName, applicationName, environmentName, regionName, instanceName));
        return model.showAllClusters(
                getBaseUri() + "v1/",
                        applicationIdentifier(tenantName, applicationName, environmentName, regionName, instanceName));
    }


    @Produces(MediaType.TEXT_HTML)
    public interface HtmlProxyHack {
        @GET
        String proxy();
    }

    @GET
    @Path("v1/legacy/tenant/{tenantName}/application/{applicationName}/environment/{environmentName}/region/{regionName}/instance/{instanceName}/service/{serviceIdentifier}/{apiParams: .*}")
    @Produces(MediaType.TEXT_HTML)
    public String htmlProxy(@PathParam("tenantName") String tenantName,
            @PathParam("applicationName") String applicationName,
            @PathParam("environmentName") String environmentName,
            @PathParam("regionName") String regionName,
            @PathParam("instanceName") String instanceName,
            @PathParam("serviceIdentifier") String identifier,
            @PathParam("apiParams") String apiParams) {
        ServiceModel model = new ServiceModel(getModelConfig(tenantName, applicationName, environmentName, regionName, instanceName));
        Service s = model.getService(identifier);
        int requestedPort = s.matchIdentifierWithPort(identifier);
        Client client = ClientBuilder.newClient();
        try {
            final StringBuilder uriBuffer = new StringBuilder("http://").append(s.host).append(':').append(requestedPort).append('/')
                    .append(apiParams);
            addQuery(uriBuffer);
            String uri = uriBuffer.toString();
            WebTarget target = client.target(uri);
            HtmlProxyHack resource = WebResourceFactory.newResource(HtmlProxyHack.class, target);
            return resource.proxy();
        } finally {
            client.close();
        }
    }

    private String getBaseUri() {
        String baseUri = uriInfo.getBaseUri().toString();
        if (baseUri.endsWith("/")) {
            return baseUri;
        } else {
            return baseUri + "/";
        }
    }

    protected ModelResponse getModelConfig(String tenant, String application, String environment, String region, String instance) {
        Client client = ClientBuilder.newClient();
        try {
            WebTarget target = client.target("http://" + host + ":" + restApiPort + "/");
            ConfigClient resource = WebResourceFactory.newResource(ConfigClient.class, target);
            return resource.getServiceModel(tenant, application, environment, region, instance);
        } finally {
            client.close();
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    @GET
    @Path("v1/tenant/{tenantName}/application/{applicationName}/environment/{environmentName}/region/{regionName}/instance/{instanceName}/service/{serviceIdentifier}/{apiParams: .*}")
    @Produces(MediaType.APPLICATION_JSON)
    public HashMap singleService(@PathParam("tenantName") String tenantName,
            @PathParam("applicationName") String applicationName,
            @PathParam("environmentName") String environmentName,
            @PathParam("regionName") String regionName,
            @PathParam("instanceName") String instanceName,
            @PathParam("serviceIdentifier") String identifier,
            @PathParam("apiParams") String apiParams) {
        ServiceModel model = new ServiceModel(getModelConfig(tenantName, applicationName, environmentName, regionName, instanceName));
        Service s = model.getService(identifier);
        int requestedPort = s.matchIdentifierWithPort(identifier);
        Client client = ClientBuilder.newClient();
        try {
            HealthClient resource = getHealthClient(apiParams, s, requestedPort, client);
            HashMap<?, ?> apiResult = resource.getHealthInfo();
            rewriteResourceLinks(apiResult, model, s, applicationIdentifier(tenantName, applicationName, environmentName, regionName, instanceName), identifier);
            return apiResult;
        } finally {
            client.close();
        }
    }

    protected HealthClient getHealthClient(String apiParams, Service s, int requestedPort, Client client) {
        final StringBuilder uriBuffer = new StringBuilder("http://").append(s.host).append(':').append(requestedPort).append('/')
                .append(apiParams);
        addQuery(uriBuffer);
        WebTarget target = client.target(uriBuffer.toString());
        return WebResourceFactory.newResource(HealthClient.class, target);
    }

    private String applicationIdentifier(String tenant, String application, String environment, String region, String instance) {
        return "tenant/" + tenant
               + "/application/" + application
               + "/environment/" + environment
               + "/region/" + region
               + "/instance/" + instance;
    }

    private void rewriteResourceLinks(Object apiResult,
            ServiceModel model,
            Service self,
            String applicationIdentifier,
            String incomingIdentifier) {
        if (apiResult instanceof List) {
            for (@SuppressWarnings("unchecked") ListIterator<Object> i = ((List<Object>) apiResult).listIterator(); i.hasNext();) {
                Object resource = i.next();
                if (resource instanceof String) {
                    try {
                        StringBuilder buffer = linkBuffer(applicationIdentifier);
                        // if it points to a port and host not part of the application, rewriting will not occur, so this is kind of safe
                        retarget(model, self, buffer, (String) resource);
                        i.set(buffer.toString());
                    } catch (GiveUpLinkRetargetingException e) {
                        break; // assume relatively homogenous lists when doing rewrites to avoid freezing up on scanning long lists
                    }
                } else {
                    rewriteResourceLinks(resource, model, self, applicationIdentifier, incomingIdentifier);
                }
            }
        } else if (apiResult instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> api = (Map<Object, Object>) apiResult;
            for (Map.Entry<Object, Object> entry : api.entrySet()) {
                if (SINGLE_API_LINK.equals(entry.getKey()) && entry.getValue() instanceof String) {
                    try {
                        rewriteSingleLink(entry, model, self, linkBuffer(applicationIdentifier));
                    } catch (GiveUpLinkRetargetingException e) {
                        // NOP
                    }
                } else if ("link".equals(entry.getKey()) && entry.getValue() instanceof String) {
                    buildSingleLink(entry, linkBuffer(applicationIdentifier), incomingIdentifier);
                } else {
                    rewriteResourceLinks(entry.getValue(), model, self, applicationIdentifier, incomingIdentifier);
                }
            }
        }
    }

    private void buildSingleLink(Map.Entry<Object, Object> entry,
                                 StringBuilder newUri,
                                 String incomingIdentifier) {
        newUri.append("/service/")
                .append(incomingIdentifier);
        newUri.append(entry.getValue());
        entry.setValue(newUri.toString());
    }

    private void addQuery(StringBuilder newUri) {
        String query = uriInfo.getRequestUri().getRawQuery();
        if (query != null && query.length() > 0) {
            newUri.append('?').append(query);
        }
    }

    private StringBuilder linkBuffer(String applicationIdentifier) {
        StringBuilder newUri = new StringBuilder(getBaseUri());
        newUri.append("v1/").append(applicationIdentifier);
        return newUri;
    }

    private void rewriteSingleLink(Map.Entry<Object, Object> entry,
            ServiceModel model,
            Service self,
            StringBuilder newUri) throws GiveUpLinkRetargetingException {
        String url = (String) entry.getValue();
        retarget(model, self, newUri, url);
        entry.setValue(newUri.toString());
    }

    private void retarget(ServiceModel model, Service self, StringBuilder newUri, String url) throws GiveUpLinkRetargetingException {
        URI link;
        try {
            link = new URI(url);
        } catch (URISyntaxException e) {
            throw new GiveUpLinkRetargetingException(e);
        }
        if (!link.isAbsolute()) {
            throw new GiveUpLinkRetargetingException("This rewriting only supports absolute URIs.");
        }
        int linkPort = link.getPort();
        if (linkPort == -1) {
            linkPort = 80;
        }
        Service s;
        try {
            s = model.resolve(link.getHost(), linkPort, self);
        } catch (IllegalArgumentException e) {
            throw new GiveUpLinkRetargetingException(e);
        }
        newUri.append("/service/").append(s.getIdentifier(linkPort));
        newUri.append(link.getRawPath());
    }

}
