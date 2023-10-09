// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2.request;

import com.yahoo.collections.Tuple2;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.jdisc.application.BindingMatch;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.http.HttpConfigRequest;

/**
 * A request to list config, bound to tenant and app id. Optionally bound to a config key, for request for named config.
 * 
 * @author vegardh
 *
 */
public class HttpListConfigsRequest implements TenantRequest {
    private final ConfigKey<?> key; // non-null if it's a named list request
    private final ApplicationId appId;
    private final boolean recursive;
    private final boolean fullAppId;
    
    private HttpListConfigsRequest(ConfigKey<?> key, ApplicationId appId, boolean recursive, boolean fullAppId) {
        this.key = key;
        this.appId = appId;
        this.recursive = recursive;
        this.fullAppId = fullAppId;
    }    
    
    public static HttpListConfigsRequest createFromListRequest(HttpRequest req) {
        BindingMatch<?> bm = HttpConfigRequests.getBindingMatch(req, 
                "http://*/config/v2/tenant/*/application/*/environment/*/region/*/instance/*/", 
                "http://*/config/v2/tenant/*/application/*/");
        if (bm.groupCount()>4) return createFromListRequestFullAppId(req, bm);
        return createFromListRequestSimpleAppId(req, bm);
    }
    
    private static HttpListConfigsRequest createFromListRequestSimpleAppId(HttpRequest req, BindingMatch<?> bm) {
        TenantName tenant = TenantName.from(bm.group(2));
        ApplicationName application = ApplicationName.from(bm.group(3));
        return new HttpListConfigsRequest(null, new ApplicationId.Builder().tenant(tenant).applicationName(application).build(),
                                          req.getBooleanProperty(HttpConfigRequests.RECURSIVE_QUERY_PROPERTY), false);
    }

    private static HttpListConfigsRequest createFromListRequestFullAppId(HttpRequest req, BindingMatch<?> bm) {
        String tenant = bm.group(2);
        String application = bm.group(3);
        String environment = bm.group(4);
        String region = bm.group(5);
        String instance = bm.group(6);
        
        ApplicationId appId = new ApplicationId.Builder()
                              .tenant(tenant)
                              .applicationName(application)
                              .instanceName(instance)
                              .build();
        return new HttpListConfigsRequest(null, appId,
                                          req.getBooleanProperty(HttpConfigRequests.RECURSIVE_QUERY_PROPERTY), true);
    }

    public static HttpListConfigsRequest createFromNamedListRequest(HttpRequest req) {
        // http://*/config/v2/tenant/*/application/*/*/
        // http://*/config/v2/tenant/*/application/*/*/*/
        // http://*/config/v2/tenant/*/application/*/environment/*/region/*/instance/*/*/
        // http://*/config/v2/tenant/*/application/*/environment/*/region/*/instance/*/*/*/
        BindingMatch<?> bm = HttpConfigRequests.getBindingMatch(req, 
                "http://*/config/v2/tenant/*/application/*/environment/*/region/*/instance/*/*/*/", 
                "http://*/config/v2/tenant/*/application/*/*/*/");
        if (bm.groupCount()>6) return createFromNamedListRequestFullAppId(req, bm);
        return createFromNamedListRequestSimpleAppId(req, bm);
    }

    private static HttpListConfigsRequest createFromNamedListRequestSimpleAppId(HttpRequest req, BindingMatch<?> bm) {
        TenantName tenant = TenantName.from(bm.group(2));
        ApplicationName application = ApplicationName.from(bm.group(3));
        String conf = bm.group(4);
        String cId;
        String cName;
        String cNamespace;
        if (bm.groupCount() >= 6) {
            cId = bm.group(5);
        } else {
            cId = "";
        }
        Tuple2<String, String> nns = HttpConfigRequest.nameAndNamespace(conf);
        cName = nns.first;
        cNamespace = nns.second;
        ConfigKey<?> key = new ConfigKey<>(cName, cId, cNamespace); 
        return new HttpListConfigsRequest(key, new ApplicationId.Builder().tenant(tenant).applicationName(application).build(),
                                          req.getBooleanProperty(HttpConfigRequests.RECURSIVE_QUERY_PROPERTY), false);
    }

    private static HttpListConfigsRequest createFromNamedListRequestFullAppId(HttpRequest req, BindingMatch<?> bm) {
        String tenant = bm.group(2);
        String application = bm.group(3);
        String environment = bm.group(4);
        String region = bm.group(5);
        String instance = bm.group(6);
        String conf = bm.group(7);
        String cId;
        String cName;
        String cNamespace;
        if (bm.groupCount() >= 9) {
            cId = bm.group(8);
        } else {
            cId = "";
        }
        Tuple2<String, String> nns = HttpConfigRequest.nameAndNamespace(conf);
        cName = nns.first;
        cNamespace = nns.second;
        ConfigKey<?> key = new ConfigKey<>(cName, cId, cNamespace);
        ApplicationId appId = new ApplicationId.Builder()
                              .tenant(tenant)
                              .applicationName(application)
                              .instanceName(instance)
                              .build();
        return new HttpListConfigsRequest(key, appId,
                                          req.getBooleanProperty(HttpConfigRequests.RECURSIVE_QUERY_PROPERTY), true);
    }

    /**
     * The application id of the request
     * @return app id
     */
    @Override
    public ApplicationId getApplicationId() {
        return appId;
    }
    
    /**
     * True if the request was of the recursive form
     * @return recursive
     */
    public boolean isRecursive() {
        return recursive;
    }
    
    /**
     * True if this was created using a URL with tenant, application, environment, region and instance; false if only tenant and application
     * @return true if full app id used
     */
    public boolean isFullAppId() {
        return fullAppId;
    }
    
    /**
     * Returns the config key of the request if it was for a named config, or null if it was just a listing request
     * @return key or null
     */
    public ConfigKey<?> getKey() {
        return key;
    }

}
