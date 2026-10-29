// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.search.skill;

import com.yahoo.component.annotation.Inject;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.JdiscBindingsConfig;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.restapi.RestApi;
import com.yahoo.restapi.RestApiException;
import com.yahoo.restapi.RestApiRequestHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Serves the {@link SchemaSkill} of this application:
 * the overview of the schemas at /skill/v1/, and the details of each schema at /skill/v1/[schema].
 * The routes are fixed, so this must be bound to /skill/v1 and /skill/v1/*.
 *
 * @author ingrid
 */
public class SkillHandler extends RestApiRequestHandler<SkillHandler> {

    static final String path = "/skill/v1";
    static final String searchHandlerId = "com.yahoo.search.handler.SearchHandler";

    private final SchemaSkill skill;
    private final String overview;

    @Inject
    public SkillHandler(ThreadedHttpRequestHandler.Context context, SchemaSkill skill, JdiscBindingsConfig bindingsConfig) {
        super(context, SkillHandler::createRestApiDefinition);
        this.skill = skill;
        this.overview = skill.overview(searchPaths(bindingsConfig), schemaName -> path + "/" + schemaName);
    }

    private static RestApi createRestApiDefinition(SkillHandler self) {
        return RestApi.builder()
                      .addRoute(RestApi.route(path).get(self::getOverview))
                      .addRoute(RestApi.route(path + "/{schema}").get(self::getSchema))
                      .build();
    }

    private HttpResponse getOverview(RestApi.RequestContext context) {
        return new MarkdownResponse(overview);
    }

    private HttpResponse getSchema(RestApi.RequestContext context) {
        String name = context.pathParameters().getStringOrThrow("schema");
        return skill.schema(name)
                    .map(MarkdownResponse::new)
                    .orElseThrow(() -> new RestApiException.NotFound("No schema named '" + name + "'. The schemas are listed at " + path + "/"));
    }

    /** Returns the paths the search handler is bound to, such as /search/. */
    static List<String> searchPaths(JdiscBindingsConfig bindingsConfig) {
        var searchHandler = bindingsConfig.handlers(searchHandlerId);
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
