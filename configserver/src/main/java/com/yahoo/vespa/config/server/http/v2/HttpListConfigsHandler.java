// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

import com.google.inject.Inject;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.path.Path;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.server.RequestHandler;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.http.HttpConfigResponse;
import com.yahoo.vespa.config.server.http.HttpHandler;
import com.yahoo.vespa.config.server.http.Utils;
import static com.yahoo.jdisc.http.HttpResponse.Status.*;

/**
 * Handler for a list configs operation. Lists all configs in model for a given application and tenant.
 *
 * @author vegardh
 * @since 5.3
 */
public class HttpListConfigsHandler extends HttpHandler {
    private final TenantRepository tenantRepository;
    private final Zone zone;
    
    @Inject
    public HttpListConfigsHandler(HttpHandler.Context ctx,
                                  TenantRepository tenantRepository, Zone zone)
    {
        super(ctx);
        this.tenantRepository = tenantRepository;
        this.zone = zone;
    }
    
    @Override
    public HttpResponse handleGET(HttpRequest req) {
        HttpListConfigsRequest listReq = HttpListConfigsRequest.createFromListRequest(req);
        RequestHandler requestHandler = HttpConfigRequests.getRequestHandler(tenantRepository, listReq);
        ApplicationId appId = listReq.getApplicationId();
        Set<ConfigKey<?>> configs = requestHandler.listConfigs(appId, Optional.empty(), listReq.isRecursive());
        String urlBase = getUrlBase(req, listReq, appId, zone);
        Set<ConfigKey<?>> allConfigs = requestHandler.allConfigsProduced(appId, Optional.empty());
        return new ListConfigsResponse(configs, allConfigs, urlBase, listReq.isRecursive());
    }

    static String getUrlBase(HttpRequest req, HttpListConfigsRequest listReq, ApplicationId appId, Zone zone) {
        if (listReq.isFullAppId())
            return Utils.getUrlBase(req,
                    Path.fromString("/config/v2/tenant/").
                    append(appId.tenant().value()).
                    append("application").
                    append(appId.application().value()).
                    append("environment").
                    append(zone.environment().value()).
                    append("region").
                    append(zone.region().value()).
                    append("instance").
                    append(appId.instance().value()).getAbsolute()+"/");
        else
            return Utils.getUrlBase(req,
                    Path.fromString("/config/v2/tenant/").
                    append(appId.tenant().value()).
                    append("application").
                    append(appId.application().value()).getAbsolute()+"/");
    }
        
    static class ListConfigsResponse extends HttpResponse {
        private final List<ConfigKey<?>> configs;
        private final Set<ConfigKey<?>> allConfigs;
        private final String urlBase;
        private final boolean recursive;

        /**
         * New list response
         *
         * @param configs the configs to include in the list
         * @param urlBase for example "http://foo.com:19071/config/v1/ (configs are appended to the listed URLs based on configs list)
         * @param recursive list recursively
         */
        public ListConfigsResponse(Set<ConfigKey<?>> configs, Set<ConfigKey<?>> allConfigs, String urlBase, boolean recursive) {
            super(OK);
            this.configs = new ArrayList<>(configs);
            Collections.sort(this.configs);
            this.allConfigs = allConfigs;
            this.urlBase = urlBase;
            this.recursive = recursive;
        }

        /**
         * The listing URL for this config in this service
         *
         * @param key config key
         * @param rec recursive
         * @return url
         */
        public String toUrl(ConfigKey<?> key, boolean rec) {
            return urlBase + key.getNamespace() + "." + key.getName() + configIdUrlPart(rec, key.getConfigId());
        }

        // Do not end with / if it's a recursive listing. Furthermore, don't do it if it's the empty config id (special handling of the root config id).
        private String configIdUrlPart(boolean rec, String configId) {
            if ("".equals(configId)) return "";
            if (rec) return "/" + configId;
            return "/" + configId + "/";
        }

        @Override
        public void render(OutputStream outputStream) throws IOException {
            Slime slime = new Slime();
            Cursor root = slime.setObject();
            Cursor array;
            if (!recursive) {
                array = root.setArray("children");
                for (ConfigKey<?> key : keysThatHaveAChildWithSameName(configs, allConfigs)) {
                    array.addString(toUrl(key, false));
                }
            }
            array = root.setArray("configs");
            
            Collection<ConfigKey<?>> cfs;
            if (recursive) {
                cfs = configs;
            } else {
                cfs = withParentConfigId(configs);
            }
            for (ConfigKey<?> key : cfs) {
                array.addString(toUrl(key, true));
            }
            new JsonFormat(true).encode(outputStream, slime);
        }

        public static Set<ConfigKey<?>> keysThatHaveAChildWithSameName(Collection<ConfigKey<?>> keys, Set<ConfigKey<?>> allConfigs) {
            Set<ConfigKey<?>> ret = new LinkedHashSet<>();
            for (ConfigKey<?> k : keys) {
                if (ListConfigsResponse.hasAChild(k, allConfigs)) ret.add(k);
            }
            return ret;
        }

        // Do we do this already somewhere?
        private static Set<ConfigKey<?>> withParentConfigId(Collection<ConfigKey<?>> keys) {
            Set<ConfigKey<?>> ret = new LinkedHashSet<>();
            for (ConfigKey<?> k : keys) {
                ret.add(new ConfigKey<>(k.getName(), parentConfigId(k.getConfigId()), k.getNamespace()));
            }
            return ret;
        }
        
        static String parentConfigId(String id) {
            if (id==null) return null;
            if (!id.contains("/")) return "";
            return id.substring(0, id.lastIndexOf('/'));
        }
        
        static boolean hasAChild(ConfigKey<?> key, Set<ConfigKey<?>> keys) {
            if ("".equals(key.getConfigId())) return false;
            for (ConfigKey<?> k : keys) {
                if (!k.getName().equals(key.getName())) continue;
                if ("".equals(k.getConfigId())) continue;
                if (k.getConfigId().equals(key.getConfigId())) continue;
                if (k.getConfigId().startsWith(key.getConfigId())) return true;
            }
            return false;
        }

        @Override
        public String getContentType() {
            return HttpConfigResponse.JSON_CONTENT_TYPE;
        }
    }
    
}
