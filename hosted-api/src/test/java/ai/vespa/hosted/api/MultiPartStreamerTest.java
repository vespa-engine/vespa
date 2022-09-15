// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiPartStreamerTest {

    @Test
    void test(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("file");
        Files.write(file, new byte[]{0x48, 0x69});
        MultiPartStreamer streamer = new MultiPartStreamer("My boundary");

        assertEquals("--My boundary--",
                     new String(streamer.data().readAllBytes()));

        streamer.addData("data", "uss/enterprise", "lore")
                .addJson("json", "{\"xml\":false}")
                .addText("text", "Hello!")
                .addFile("file", file);

        String expected = """
                          --My boundary\r
                          Content-Disposition: form-data; name="data"\r
                          Content-Type: uss/enterprise\r
                          \r
                          lore\r
                          --My boundary\r
                          Content-Disposition: form-data; name="json"\r
                          Content-Type: application/json\r
                          \r
                          {"xml":false}\r
                          --My boundary\r
                          Content-Disposition: form-data; name="text"\r
                          Content-Type: text/plain\r
                          \r
                          Hello!\r
                          --My boundary\r
                          Content-Disposition: form-data; name="file"; filename="%s"\r
                          Content-Type: application/octet-stream\r
                          \r
                          Hi\r
                          --My boundary--""".formatted(file.getFileName());

        assertEquals(expected,
                     new String(streamer.data().readAllBytes()));

        // Verify that all data is read again for a new builder.
        assertEquals(expected,
                     new String(streamer.data().readAllBytes()));

        assertEquals(List.of("multipart/form-data; boundary=My boundary; charset=utf-8"),
                     streamer.streamTo(HttpRequest.newBuilder(), Method.POST)
                             .uri(URI.create("https://uri/path"))
                             .build().headers().allValues("Content-Type"));
    }

}
