// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.search.skill;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.JdiscBindingsConfig;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static com.yahoo.jdisc.http.HttpRequest.Method.POST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author ingrid
 */
public class SkillHandlerTest {

    private final SchemaSkill skill = new SchemaSkill(SchemaSkillTest.schemaInfo());
    private final SkillHandler handler = new SkillHandler(ThreadedHttpRequestHandler.testContext(), skill,
                                                          bindings("http://*/search/*", "http://*/search"));

    @Test
    void testIndex() throws IOException {
        for (String path : List.of("/skills/v1", "/skills/v1/")) {
            HttpResponse response = get(path);
            assertEquals(200, response.getStatus());
            assertEquals("text/markdown", response.getContentType());
            // Checks the skills listed, by name and path, not the wording around them
            String index = body(response);
            assertTrue(index.contains(SchemaSkill.NAME), index);
            assertTrue(index.contains("`/skills/v1/schemas`"), index);
        }
    }

    @Test
    void testOverview() throws IOException {
        String expected = skill.overview(List.of("/search/"), name -> "/skills/v1/schemas/" + name);
        assertMarkdown(expected, get("/skills/v1/schemas"));
        assertMarkdown(expected, get("/skills/v1/schemas/"));
    }

    @Test
    void testSearchPaths() {
        assertEquals(List.of("/search/"), SkillHandler.searchPaths(bindings("http://*/search/*", "http://*/search")));
        assertEquals(List.of("/search/"), SkillHandler.searchPaths(bindings("http://*:4443/search/*", "http://*:4443/search",
                                                                            "http://*:4444/search/*", "http://*:4444/search")));
        assertEquals(List.of("/search-binding/"), SkillHandler.searchPaths(bindings("http://*/search-binding/")));
        assertEquals(List.of(), SkillHandler.searchPaths(new JdiscBindingsConfig.Builder().build()));
    }

    @Test
    void testSchema() throws IOException {
        assertMarkdown(skill.schema("product").get(), get("/skills/v1/schemas/product"));
        assertMarkdown(skill.schema("mail").get(), get("/skills/v1/schemas/mail/"));
    }

    @Test
    void testUnknownSchema() throws IOException {
        HttpResponse response = get("/skills/v1/schemas/nonexisting");
        assertEquals(404, response.getStatus());
        assertTrue(body(response).contains("No schema named 'nonexisting'. The schemas are listed at /skills/v1/schemas/"), body(response));
    }

    @Test
    void testOnlyGetIsSupported() {
        HttpResponse response = handler.handle(HttpRequest.createTestRequest("http://localhost/skills/v1/schemas/", POST));
        assertEquals(405, response.getStatus());
    }

    private HttpResponse get(String path) {
        return handler.handle(HttpRequest.createTestRequest("http://localhost" + path, GET));
    }

    private static JdiscBindingsConfig bindings(String ... searchBindings) {
        return new JdiscBindingsConfig.Builder()
                .handlers(SkillHandler.SEARCH_HANDLER_ID, new JdiscBindingsConfig.Handlers.Builder().serverBindings(List.of(searchBindings)))
                .build();
    }

    private static void assertMarkdown(String expected, HttpResponse response) throws IOException {
        assertEquals(200, response.getStatus());
        assertEquals("text/markdown", response.getContentType());
        assertEquals(expected, body(response));
    }

    private static String body(HttpResponse response) throws IOException {
        var out = new ByteArrayOutputStream();
        response.render(out);
        return out.toString(StandardCharsets.UTF_8);
    }

}
