// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.client.openai;

import ai.vespa.llm.completion.Completion;
import ai.vespa.llm.completion.StringPrompt;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * @author bratseth
 */
public class OldOpenAiClientCompletionTest {

//    @Test
    public void testLocalAsync() throws IOException, URISyntaxException {
        var slime = new Slime();
        var root = slime.setObject();

        // Do an embedding search instead
        var query = "what is the oldest skyscraper city in the world?";

        // This could actually be set up by the LLM as well

        // Set up search
        root.setString("yql", "select documentid,title,abstract from news where userQuery()");
        root.setLong("hits", 10);
        root.setString("query", query);
        root.setString("type", "any");
        root.setString("format", "TokenRenderer");
        root.setString("searchChain", "gan");
        root.setString("traceLevel", "5");

        // Which fields? Aha, that is from the yql actually
        // Or the result

        var prompt = "Given the following json query structure:\n" +
                "{\n" +
                "  yql: select title,abstract from news where documentid=###:\n" +
                "}\n" +
                "\n" +
                "Write a query with the above structure where the documentid is filled in with the documentid of the document that contains the information about the oldest scyscraper city.";
        root.setString("prompt", prompt);

        // Hva med eventuelle feil? Dvs for mange tokens?
        // OG budsjett er feil etc etc
        // Renderer måta hensyn til det da....

        // Set up prompt
//        root.setString("prompt", "What document contains the information about the oldest skyscaper city? What is the documentid? Answer using json, with only the documentid.");
//        root.setString("prompt", "In the longest possible way to answer, what is the oldest skryscraper city?");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("http://localhost:8080/search/"))
                .header("Content-Type", "application/json")
                .header("X-LLM-API-KEY", "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")
                .POST(HttpRequest.BodyPublishers.ofByteArray(SlimeUtils.toJsonBytes(slime)))
                .build();

        HttpClient client = HttpClient.newBuilder().build();
        CompletableFuture<HttpResponse<Stream<String>>> futureResponse = client.sendAsync(request,
                HttpResponse.BodyHandlers.ofLines());

        futureResponse.thenAcceptAsync(response -> {
            int responseCode = response.statusCode();
            System.out.println("Response Code: " + responseCode);

            // What about errors here?

            final int[] counter = {0, 0};
            Stream<String> lines = response.body();
            lines.forEach(line -> {
                System.out.println(line);
                Cursor cursor = SlimeUtils.jsonToSlime(line).get();
                String id = cursor.field("id").asString();
                String token = cursor.field("token").asString();

                if (counter[0] > 80 && token.startsWith(" ")) {
                    System.out.println();
                    counter[0] = 0;
                    token = token.substring(1);
                }
                System.out.print(token);
//            System.out.print("(" + counter[1] + ")");
                System.out.flush();

                if (token.contains("\n")) {
                    counter[0] = 0;
                } else {
                    counter[0] += token.length();
                }
                counter[1]++;
            });
        }).join();

//        futureResponse.join();

    }

}
