// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import com.yahoo.restapi.UriBuilder;
import com.yahoo.vespa.serviceview.bindings.ApplicationView;
import com.yahoo.vespa.serviceview.bindings.ClusterView;
import com.yahoo.vespa.serviceview.bindings.ServiceView;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A response containing a service view for an application deployment.
 * This does not define the API response but merely proxies the API response provided by Vespa, with URLs
 * rewritten to include zone and application information allow proxying through the controller
 * 
 * @author Steinar Knutsen
 * @author bratseth
 */
class ServiceApiResponse extends HttpResponse {

    private final ZoneId zone;
    private final ApplicationId application;
    private final List<URI> configServerURIs;
    private final Slime slime;
    private final UriBuilder requestUri;

    // Only set for one of the setResponse calls
    private String serviceName = null;
    private String restPath = null;
    
    public ServiceApiResponse(ZoneId zone, ApplicationId application, List<URI> configServerURIs, URI requestUri) {
        super(200);
        this.zone = zone;
        this.application = application;
        this.configServerURIs = configServerURIs;
        this.slime = new Slime();
        this.requestUri = new UriBuilder(requestUri).withoutParameters();
    }
    
    public void setResponse(ApplicationView applicationView) {
        Cursor clustersArray = slime.setObject().setArray("clusters");
        for (ClusterView clusterView : applicationView.clusters) {
            Cursor clusterObject = clustersArray.addObject();
            clusterObject.setString("name", clusterView.name);
            clusterObject.setString("type", clusterView.type);
            setNullableString("url", rewriteIfUrl(clusterView.url, requestUri), clusterObject);
            Cursor servicesArray = clusterObject.setArray("services");
            for (ServiceView serviceView : clusterView.services) {
                Cursor serviceObject = servicesArray.addObject();
                setNullableString("url", rewriteIfUrl(serviceView.url, requestUri), serviceObject);
                serviceObject.setString("serviceType", serviceView.serviceType);
                serviceObject.setString("serviceName", serviceView.serviceName);
                serviceObject.setString("configId", serviceView.configId);
                serviceObject.setString("host", serviceView.host);
            }
        }
    }
    
    public void setResponse(Map<?,?> responseData, String serviceName, String restPath) {
        this.serviceName = serviceName;
        this.restPath = restPath;
        mapToSlime(responseData, slime.setObject());
    }

    @Override
    public void render(OutputStream stream) throws IOException {
        new JsonFormat(true).encode(stream, slime);
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

    @SuppressWarnings("unchecked")
    private void mapToSlime(Map<?,?> data, Cursor object) {
        for (Map.Entry<String, Object> entry : ((Map<String, Object>)data).entrySet())
            fieldToSlime(entry.getKey(), entry.getValue(), object);
    }
    
    private void fieldToSlime(String key, Object value, Cursor object) {
        if (value instanceof String) {
            if (key.equals("url") || key.equals("link"))
                value = rewriteIfUrl((String)value, generateLocalLinkPrefix(serviceName, restPath));
            setNullableString(key, (String)value, object);
        }
        else if (value instanceof Integer) {
            object.setLong(key, (int)value);
        }
        else if (value instanceof Long) {
            object.setLong(key, (long)value);
        }
        else if (value instanceof Float) {
            object.setDouble(key, (double)value);
        }
        else if (value instanceof Double) {
            object.setDouble(key, (double)value);
        }
        else if (value instanceof List) {
            listToSlime((List)value, object.setArray(key));
        }
        else if (value instanceof Map) {
            mapToSlime((Map<?,?>)value, object.setObject(key));
        }
    }

    private void listToSlime(List<?> list, Cursor array) {
        for (Object entry : list)
            entryToSlime(entry, array);
    }

    private void entryToSlime(Object entry, Cursor array) {
        if (entry instanceof String)
            addNullableString(rewriteIfUrl((String)entry, generateLocalLinkPrefix(serviceName, restPath)), array);
        else if (entry instanceof Integer)
            array.addLong((long)entry);
        else if (entry instanceof Long)
            array.addLong((long)entry);
        else if (entry instanceof Float)
            array.addDouble((double)entry);
        else if (entry instanceof Double)
            array.addDouble((double)entry);
        else if (entry instanceof List)
            listToSlime((List)entry, array.addArray());
        else if (entry instanceof Map)
            mapToSlime((Map)entry, array.addObject());
    }

    private String rewriteIfUrl(String urlOrAnyString, UriBuilder requestUri) {
        if (urlOrAnyString == null) return null;

        String hostPattern = "(" +
                             String.join(
                                     "|", configServerURIs.stream()
                                             .map(URI::toString)
                                             .map(s -> s.substring(0, s.length() -1))
                                             .map(Pattern::quote)
                                             .toArray(String[]::new))
                             + ")";

        String remoteServicePath = "/serviceview/"
                                   + "v1/tenant/" + application.tenant().value()
                                   + "/application/" + application.application().value()
                                   + "/environment/" + zone.environment().value()
                                   + "/region/" + zone.region().value()
                                   + "/instance/" + application.instance()
                                   + "/service/";

        Pattern remoteServiceResourcePattern = Pattern.compile("^(" + hostPattern + Pattern.quote(remoteServicePath) + ")");
        Matcher matcher = remoteServiceResourcePattern.matcher(urlOrAnyString);

        if (matcher.find()) {
            String proxiedPath = urlOrAnyString.substring(matcher.group().length());
            return requestUri.append(proxiedPath).toString();
        } else {
            return urlOrAnyString; // not a service url
        }
    }

    private UriBuilder generateLocalLinkPrefix(String identifier, String restPath) {
        String proxiedPath = identifier + "/" + restPath;

        if (this.requestUri.toString().endsWith(proxiedPath)) {
            return new UriBuilder(this.requestUri.toString().substring(0, this.requestUri.toString().length() - proxiedPath.length()));
        } else {
            throw new IllegalStateException("Expected the resource path '" + this.requestUri + "' to end with '" + proxiedPath + "'");
        }
    }

    private void setNullableString(String key, String valueOrNull, Cursor receivingObject) {
        if (valueOrNull == null)
            receivingObject.setNix(key);
        else
            receivingObject.setString(key, valueOrNull);
    }

    private void addNullableString(String valueOrNull, Cursor receivingArray) {
        if (valueOrNull == null)
            receivingArray.addNix();
        else
            receivingArray.addString(valueOrNull);
    }

}
