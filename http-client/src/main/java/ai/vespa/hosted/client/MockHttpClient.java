// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.client;

import ai.vespa.http.HttpURL;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.HttpEntities;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author jonmv
 */
public class MockHttpClient extends AbstractHttpClient {

    private final Deque<Expectation> expectations = new ArrayDeque<>();

    @Override
    protected ClassicHttpResponse execute(ClassicHttpRequest request, HttpClientContext context) throws IOException {
        Expectation expectation = expectations.poll();
        if (expectation == null)
            throw new AssertionError("No further requests expected, but got " + request);

        return expectation.handle(request);
    }

    @Override
    public void close() {
        if ( ! expectations.isEmpty())
            throw new AssertionError(expectations.size() + " more requests were expected");
    }

    public void expect(Expectation expectation) {
        expectations.add(expectation);
    }

    public void expect(Function<ClassicHttpRequest, String> mapper, int status) {
        expect(request -> {
            BasicClassicHttpResponse response = new BasicClassicHttpResponse(status);
            response.setEntity(HttpEntities.create(mapper.apply(request), ContentType.APPLICATION_JSON));
            return response;
        });
    }

    public void expect(BiFunction<HttpURL, String, String> mapper, int status) {
        expect(request -> {
            try {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                if (request.getEntity() != null)
                    request.getEntity().writeTo(buffer);

                BasicClassicHttpResponse response = new BasicClassicHttpResponse(status);
                response.setEntity(HttpEntities.create(mapper.apply(HttpURL.from(request.getUri()), buffer.toString(UTF_8)),
                                                       ContentType.APPLICATION_JSON));
                return response;
            }
            catch (URISyntaxException e) {
                throw new IllegalArgumentException(e);
            }
        });
    }

    @FunctionalInterface
    public interface Expectation {

        ClassicHttpResponse handle(ClassicHttpRequest request) throws IOException;

    }

}
