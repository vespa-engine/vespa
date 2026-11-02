// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.search.skill;

import com.yahoo.component.annotation.Inject;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.JdiscBindingsConfig;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.restapi.RestApi;
import com.yahoo.restapi.RestApiException;
import com.yahoo.restapi.RestApiRequestHandler;
import com.yahoo.search.handler.SearchHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Serves the agent skills of this application: a list of them at /skills/v1/, and each skill at /skills/v1/[skill].
 * The routes are fixed, so this must be bound to /skills/v1 and /skills/v1/*.
 *
 * The only skill so far is the {@link SchemaSkill}, at /skills/v1/schemas, so this also holds what is specific to it:
 * the routes to its schema documents, and the search paths it tells agents to query. When another skill is added,
 * separate that out, either into a handler per skill with one that only lists them, or by having skills
 * implement a common interface which this serves.
 *
 * @author ingrid
 */
public class SkillHandler extends RestApiRequestHandler<SkillHandler> {

    static final String PATH = "/skills/v1";
    static final String SCHEMAS_PATH = PATH + "/schemas";
    static final String SEARCH_HANDLER_ID = SearchHandler.class.getName();

    private final SchemaSkill skill;
    private final String overview;

    @Inject
    public SkillHandler(ThreadedHttpRequestHandler.Context context, SchemaSkill skill, JdiscBindingsConfig bindingsConfig) {
        super(context, SkillHandler::createRestApiDefinition);
        this.skill = skill;
        this.overview = skill.overview(searchPaths(bindingsConfig), schemaName -> SCHEMAS_PATH + "/" + schemaName);
    }

    private static RestApi createRestApiDefinition(SkillHandler self) {
        return RestApi.builder()
                      .addRoute(RestApi.route(PATH).get(self::getIndex))
                      .addRoute(RestApi.route(SCHEMAS_PATH).get(self::getOverview))
                      .addRoute(RestApi.route(SCHEMAS_PATH + "/{schema}").get(self::getSchema))
                      .build();
    }

    private HttpResponse getIndex(RestApi.RequestContext context) {
        return new MarkdownResponse(index());
    }

    private HttpResponse getOverview(RestApi.RequestContext context) {
        return new MarkdownResponse(overview);
    }

    private HttpResponse getSchema(RestApi.RequestContext context) {
        String name = context.pathParameters().getStringOrThrow("schema");
        return skill.schema(name)
                    .map(MarkdownResponse::new)
                    .orElseThrow(() -> new RestApiException.NotFound("No schema named '" + name + "'. The schemas are listed at " + SCHEMAS_PATH + "/"));
    }

    /**
     * Returns the list of the skills served, with where to find each.
     * This handler is only added when the schemas skill is enabled, so that is the only one listed. With more
     * skills, this must list only those enabled in services.xml, e.g. from the skill components injected here.
     */
    static String index() {
        return "# Agent skills of this application\n\n" +
               "Each skill is a SKILL.md document at the given path, which may link to more documents.\n\n" +
               "- `" + SchemaSkill.NAME + "` at `" + SCHEMAS_PATH + "`: " + SchemaSkill.DESCRIPTION + "\n";
    }

    /** Returns the paths the search handler is bound to, such as /search/. */
    static List<String> searchPaths(JdiscBindingsConfig bindingsConfig) {
        var searchHandler = bindingsConfig.handlers(SEARCH_HANDLER_ID);
        if (searchHandler == null) return List.of();
        return searchHandler.serverBindings().stream()
                            .map(SkillHandler::pathOf)
                            .filter(Objects::nonNull)
                            .map(path -> path.endsWith("*") ? path.substring(0, path.length() - 1) : path)
                            .map(path -> path.endsWith("/") ? path : path + "/")
                            .distinct()
                            .toList();
    }

    /** Returns the path of a binding such as http://*:4443/search/*, or null if it has none. */
    private static String pathOf(String binding) {
        int hostStart = binding.indexOf("://");
        if (hostStart < 0) return null;
        int pathStart = binding.indexOf('/', hostStart + 3);
        return pathStart < 0 ? null : binding.substring(pathStart);
    }

    private static class MarkdownResponse extends HttpResponse {

        private final byte[] body;

        MarkdownResponse(String body) {
            super(200);
            this.body = body.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String getContentType() { return "text/markdown"; }

        @Override
        public void render(OutputStream outputStream) throws IOException {
            outputStream.write(body);
        }

    }

}
