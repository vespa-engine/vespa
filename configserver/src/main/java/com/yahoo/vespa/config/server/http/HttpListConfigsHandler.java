// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.google.inject.Inject;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.server.RequestHandler;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.config.provision.ApplicationId;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

import static com.yahoo.jdisc.http.HttpResponse.Status.*;


/**
 * Handler for a list configs operation. Lists all configs in model.
 *
 * @author vegardh
 * @since 5.1.11
 */
public class HttpListConfigsHandler extends HttpHandler {
    static final String RECURSIVE_QUERY_PROPERTY = "recursive";
    private final RequestHandler requestHandler;

    @Inject
    public HttpListConfigsHandler(HttpHandler.Context ctx, TenantRepository tenantRepository) {
        this(ctx, tenantRepository.defaultTenant().getRequestHandler());
    }

    public HttpListConfigsHandler(HttpHandler.Context ctx, RequestHandler requestHandler) {
        super(ctx);
        this.requestHandler = requestHandler;
    }
    
    @Override
    public HttpResponse handleGET(HttpRequest req) {
        boolean recursive = req.getBooleanProperty(RECURSIVE_QUERY_PROPERTY);
        Set<ConfigKey<?>> configs = requestHandler.listConfigs(ApplicationId.defaultId(), Optional.empty(), recursive);
        String urlBase = Utils.getUrlBase(req, "/config/v1/");
        Set<ConfigKey<?>> allConfigs = requestHandler.allConfigsProduced(ApplicationId.defaultId(), Optional.empty());
        return new ListConfigsResponse(configs, allConfigs, urlBase, recursive);
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
        String toUrl(ConfigKey<?> key, boolean rec) {
            return urlBase + key.getNamespace() + "." + key.getName() + "/" + key.getConfigId() + (rec ? "" : "/");
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
            for (ConfigKey<?> key : configs) {
                array.addString(toUrl(key, true));
            }
            new JsonFormat(true).encode(outputStream, slime);
        }

        static Set<ConfigKey<?>> keysThatHaveAChildWithSameName(Collection<ConfigKey<?>> keys, Set<ConfigKey<?>> allConfigs) {
            Set<ConfigKey<?>> ret = new LinkedHashSet<>();
            for (ConfigKey<?> k : keys) {
                if (ListConfigsResponse.hasAChild(k, allConfigs)) ret.add(k);
            }
            return ret;
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
