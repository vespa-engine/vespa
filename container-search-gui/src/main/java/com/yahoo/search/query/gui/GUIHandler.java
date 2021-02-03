// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.gui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.querytransform.RecallSearcher;
import com.yahoo.restapi.Path;
import com.yahoo.search.Query;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.search.query.Model;
import com.yahoo.search.query.Presentation;
import com.yahoo.search.query.Ranking;
import com.yahoo.search.query.ranking.Diversity;
import com.yahoo.search.query.ranking.MatchPhase;
import com.yahoo.search.query.restapi.ErrorResponse;
import com.yahoo.search.yql.MinimalQueryInserter;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.yolean.Exceptions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;


/**
 * Takes requests on /querybuilder
 *
 * @author  Henrik Høiness
 */
public class GUIHandler extends LoggingRequestHandler {

    private static final ObjectMapper jsonMapper = new ObjectMapper();

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
                try { json = getGUIConfig(); } catch (IOException e) { /*Something happened while parsing JSON */ }
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

        private String getGUIConfig() throws IOException {
            ObjectNode json = jsonMapper.createObjectNode();
            json.set("ranking_properties", jsonMapper.createArrayNode().add("propertyname"));
            json.set("ranking_features", jsonMapper.createArrayNode().add("featurename"));

            ArrayNode sources = jsonMapper.createArrayNode();

            try {
                indexModel.getMasterClusters().keySet().forEach(sources::add);
            } catch (NullPointerException ex){ /* clusters are not set */ }
            json.set("model_sources", sources);

            ArrayNode rankProfiles = jsonMapper.createArrayNode();
            try {
                rankProfilesConfig.rankprofile().forEach(rankProfile -> rankProfiles.add(rankProfile.name()));
            } catch (NullPointerException ex){ /* rankprofiles are not set*/ }
            json.set("ranking_profile", rankProfiles);


            // Creating map from parent to children for GUI: parameter --> child-parameters
            ObjectNode childMap = jsonMapper.createObjectNode();
            childMap.set(Model.MODEL, jsonMapper.createArrayNode().add(Model.DEFAULT_INDEX).add(Model.ENCODING).add(Model.LANGUAGE).add(Model.QUERY_STRING).add(Model.RESTRICT).add(Model.SEARCH_PATH).add(Model.SOURCES).add(Model.TYPE));
            childMap.set(Ranking.RANKING, jsonMapper.createArrayNode().add(Ranking.LOCATION).add(Ranking.FEATURES).add(Ranking.LIST_FEATURES).add(Ranking.PROFILE).add(Ranking.PROPERTIES).add(Ranking.SORTING).add(Ranking.FRESHNESS).add(Ranking.QUERYCACHE).add(Ranking.MATCH_PHASE));
            childMap.set(Ranking.RANKING +"."+ Ranking.MATCH_PHASE, jsonMapper.createArrayNode().add(MatchPhase.MAX_HITS).add(MatchPhase.ATTRIBUTE).add(MatchPhase.ASCENDING).add(Ranking.DIVERSITY));
            childMap.set(Ranking.RANKING +"."+ Ranking.MATCH_PHASE +"."+Ranking.DIVERSITY, jsonMapper.createArrayNode().add(Diversity.ATTRIBUTE).add(Diversity.MINGROUPS));
            childMap.set(Presentation.PRESENTATION, jsonMapper.createArrayNode().add(Presentation.BOLDING).add(Presentation.FORMAT).add(Presentation.SUMMARY).add("template").add(Presentation.TIMING ));
            childMap.set("trace", jsonMapper.createArrayNode().add("timestamps"));
            childMap.set("tracelevel", jsonMapper.createArrayNode().add("rules"));
            childMap.set("metrics", jsonMapper.createArrayNode().add("ignore"));
            childMap.set("collapse", jsonMapper.createArrayNode().add("summary"));
            childMap.set("pos", jsonMapper.createArrayNode().add("ll").add("radius").add("bb").add("attribute"));
            childMap.set("streaming", jsonMapper.createArrayNode().add("userid").add("groupname").add("selection").add("priority").add("maxbucketspervisitor"));
            childMap.set("rules", jsonMapper.createArrayNode().add("off").add("rulebase"));
            json.set("childMap", childMap);

            ArrayNode levelZeroParameters = jsonMapper.createArrayNode().add(MinimalQueryInserter.YQL.toString()).add(Query.HITS.toString()).add(Query.OFFSET.toString())
                                .add("queryProfile").add(Query.NO_CACHE.toString()).add(Query.GROUPING_SESSION_CACHE.toString())
                                .add(Query.SEARCH_CHAIN.toString()).add(Query.TIMEOUT.toString()).add("trace").add("tracelevel")
                                .add(Query.TRACE_LEVEL.toString()).add(Query.EXPLAIN_LEVEL.toString()).add("explainlevel").add(Model.MODEL).add(Ranking.RANKING).add("collapse").add("collapsesize").add("collapsefield")
                                .add(Presentation.PRESENTATION).add("pos").add("streaming").add("rules").add(RecallSearcher.recallName.toString()).add("user")
                                .add("metrics").add("");
            json.set("levelZeroParameters", levelZeroParameters);

            return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        }
    }
}