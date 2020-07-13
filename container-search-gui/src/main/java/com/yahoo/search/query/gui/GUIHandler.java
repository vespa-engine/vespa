// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.gui;

import com.google.inject.Inject;

import com.yahoo.container.QrSearchersConfig;
import com.yahoo.container.jdisc.HandlerEntryPoint;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.container.jdisc.NavigableRequestHandler;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.querytransform.RecallSearcher;
import com.yahoo.restapi.Path;
import com.yahoo.search.Query;
import com.yahoo.search.query.Model;
import com.yahoo.search.query.Presentation;
import com.yahoo.search.query.Ranking;
import com.yahoo.search.query.ranking.Diversity;
import com.yahoo.search.query.ranking.MatchPhase;
import com.yahoo.search.query.restapi.ErrorResponse;
import com.yahoo.search.yql.MinimalQueryInserter;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.yolean.Exceptions;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;


/**
 * Takes requests on /querybuilder
 *
 * @author  Henrik Høiness
 */

public class GUIHandler extends LoggingRequestHandler implements NavigableRequestHandler {

    private final IndexModel indexModel;
    private final RankProfilesConfig rankProfilesConfig;

    @Inject
    public GUIHandler(Context parentContext, IndexInfoConfig indexInfo, QrSearchersConfig clusters, RankProfilesConfig rankProfilesConfig) {
        super(parentContext);
        this.indexModel = new IndexModel(indexInfo, clusters);
        this.rankProfilesConfig = rankProfilesConfig;

    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case GET: return handleGET(request);
                default: return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is not supported");
            }
        } catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "'", e);
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

    private HttpResponse handleGET(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/querybuilder/")) {
            return new FileResponse("_includes/index.html", null, null);
        }
        if (!path.matches("/querybuilder/{*}") ) {
            return ErrorResponse.notFoundError("Nothing at path:" + path);
        }
        String filepath = path.getRest();
        if (!isValidPath(filepath) && !filepath.equals("config.json")){
            return ErrorResponse.notFoundError("Nothing at path:" + filepath);
        }
        return new FileResponse(filepath, indexModel, rankProfilesConfig);
    }

    private static boolean isValidPath(String path) {
        InputStream in = GUIHandler.class.getClassLoader().getResourceAsStream("gui/"+path);
        boolean isValid = (in != null);
        if(isValid){
            try { in.close(); } catch (IOException e) {/* Problem with closing inputstream */}
        }

        return isValid;
    }

    @Override
    public List<HandlerEntryPoint> entryPoints() {
        return List.of(HandlerEntryPoint.of("./"));
    }

    private static class FileResponse extends HttpResponse {

        private final String path;
        private final IndexModel indexModel;
        private final RankProfilesConfig rankProfilesConfig;

        public FileResponse(String relativePath, IndexModel indexModel, RankProfilesConfig rankProfilesConfig) {
            super(200);
            this.path = relativePath;
            this.indexModel = indexModel;
            this.rankProfilesConfig = rankProfilesConfig;

        }

        @Override
        public void render(OutputStream out) throws IOException {
            InputStream is;
            if (this.path.equals("config.json")){
                String json = "{}";
                try { json = getGUIConfig(); } catch (JSONException e) { /*Something happened while parsing JSON */ }
                is = new ByteArrayInputStream(json.getBytes());
            } else{
                is = GUIHandler.class.getClassLoader().getResourceAsStream("gui/"+this.path);
            }
            byte[] buf = new byte[1024];
            int numRead;
            while ( (numRead = is.read(buf) ) >= 0) {
                out.write(buf, 0, numRead);
            }
        }

        @Override
        public String getContentType() {
            if (path.endsWith(".css")) {
                return "text/css";
            } else if (path.endsWith(".js")) {
                return "application/javascript";
            } else if (path.endsWith(".html")) {
                return "text/html";
            } else if (path.endsWith(".php")) {
                return "text/php";
            } else if (path.endsWith(".svg")) {
                return "image/svg+xml";
            } else if (path.endsWith(".eot")) {
                return "application/vnd.ms-fontobject";
            } else if (path.endsWith(".ttf")) {
                return "font/ttf";
            } else if (path.endsWith(".woff")) {
                return "font/woff";
            } else if (path.endsWith(".woff2")) {
                return "font/woff2";
            } else if (path.endsWith(".otf")) {
                return "font/otf";
            } else if (path.endsWith(".png")) {
                return "image/png";
            } else if (path.endsWith(".xml")) {
                return "application/xml";
            } else if (path.endsWith(".ico")) {
                return "image/x-icon";
            } else if (path.endsWith(".json")) {
                return "application/json";
            } else if (path.endsWith(".ttf")) {
                return "font/ttf";
            }
            return "text/html";
        }

        private String getGUIConfig() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("ranking_properties", Arrays.asList("propertyname"));
            json.put("ranking_features", Arrays.asList("featurename"));

            List<String> sources = new ArrayList<>();

            try {
                sources = new ArrayList<>(indexModel.getMasterClusters().keySet());
            } catch (NullPointerException ex){ /* clusters are not set */ }
            json.put("model_sources", sources);

            List<String> rankProfiles = new ArrayList<>();
            try {
                rankProfilesConfig.rankprofile().forEach(rankProfile -> rankProfiles.add(rankProfile.name()));
            } catch (NullPointerException ex){ /* rankprofiles are not set*/ }
            json.put("ranking_profile", rankProfiles);


            // Creating map from parent to children for GUI: parameter --> child-parameters
            HashMap<String, List<String>> childMap = new HashMap<>();
            childMap.put(Model.MODEL, Arrays.asList(Model.DEFAULT_INDEX, Model.ENCODING, Model.LANGUAGE, Model.QUERY_STRING, Model.RESTRICT, Model.SEARCH_PATH, Model.SOURCES, Model.TYPE));
            childMap.put(Ranking.RANKING, Arrays.asList(Ranking.LOCATION, Ranking.FEATURES, Ranking.LIST_FEATURES, Ranking.PROFILE, Ranking.PROPERTIES, Ranking.SORTING, Ranking.FRESHNESS, Ranking.QUERYCACHE, Ranking.MATCH_PHASE));
            childMap.put(Ranking.RANKING +"."+ Ranking.MATCH_PHASE, Arrays.asList(MatchPhase.MAX_HITS, MatchPhase.ATTRIBUTE, MatchPhase.ASCENDING, Ranking.DIVERSITY));
            childMap.put(Ranking.RANKING +"."+ Ranking.MATCH_PHASE +"."+Ranking.DIVERSITY, Arrays.asList(Diversity.ATTRIBUTE, Diversity.MINGROUPS));
            childMap.put(Presentation.PRESENTATION, Arrays.asList(Presentation.BOLDING, Presentation.FORMAT, Presentation.SUMMARY, "template", Presentation.TIMING ));
            childMap.put("trace", Arrays.asList("timestamps"));
            childMap.put("tracelevel", Arrays.asList("rules"));
            childMap.put("metrics", Arrays.asList("ignore"));
            childMap.put("collapse", Arrays.asList("summary"));
            childMap.put("pos", Arrays.asList("ll", "radius", "bb", "attribute"));
            childMap.put("streaming", Arrays.asList("userid", "groupname", "selection", "priority", "maxbucketspervisitor"));
            childMap.put("rules", Arrays.asList("off", "rulebase"));
            json.put("childMap", childMap);

            List<String> levelZeroParameters = Arrays.asList(MinimalQueryInserter.YQL.toString(), Query.HITS.toString(), Query.OFFSET.toString(),
                                "queryProfile", Query.NO_CACHE.toString(), Query.GROUPING_SESSION_CACHE.toString(),
                                Query.SEARCH_CHAIN.toString(), Query.TIMEOUT.toString(), "trace", "tracelevel",
                                Query.TRACE_LEVEL.toString(), Query.EXPLAIN_LEVEL.toString(), "explainlevel", Model.MODEL, Ranking.RANKING, "collapse", "collapsesize","collapsefield",
                                Presentation.PRESENTATION, "pos", "streaming", "rules", RecallSearcher.recallName.toString(), "user",
                                "metrics", "");
            json.put("levelZeroParameters", levelZeroParameters);

            return json.toString();
        }
    }
}