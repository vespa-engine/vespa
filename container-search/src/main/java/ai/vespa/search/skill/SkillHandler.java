// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.search.skill;

import com.yahoo.component.annotation.Inject;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.restapi.RestApi;
import com.yahoo.restapi.RestApiException;
import com.yahoo.restapi.RestApiRequestHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

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

    private final SchemaSkill skill;
    private final String overview;

    @Inject
    public SkillHandler(ThreadedHttpRequestHandler.Context context, SchemaSkill skill) {
        super(context, SkillHandler::createRestApiDefinition);
        this.skill = skill;
        this.overview = skill.overview();
    }

    private static RestApi createRestApiDefinition(SkillHandler self) {
        return RestApi.builder()
                      .addRoute(RestApi.route("/skills/v1").get(self::getIndex))
                      .addRoute(RestApi.route("/skills/v1/schemas").get(self::getOverview))
                      .addRoute(RestApi.route("/skills/v1/schemas/{schema}").get(self::getSchema))
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
                    .orElseThrow(() -> new RestApiException.NotFound("No schema named '" + name +
                                                                     "'. The schemas are listed at /skills/v1/schemas/"));
    }

    /**
     * Returns the list of the skills served, with where to find each.
     * This handler is only added when the schemas skill is enabled, so that is the only one listed. With more
     * skills, this must list only those enabled in services.xml, e.g. from the skill components injected here.
     */
    static String index() {
        return "# Agent skills of this application\n\n" +
               "Each skill is a SKILL.md document at the given path, which may link to more documents.\n\n" +
               "- `" + SchemaSkill.NAME + "` at `/skills/v1/schemas" + "`: " + SchemaSkill.DESCRIPTION + "\n";
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
