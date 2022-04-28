// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.client;

import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.HttpEntities;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Function;

/**
 * @author jonmv
 */
public class MockConfigServerClient extends AbstractConfigServerClient {

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

    public void expect(int status, Function<ClassicHttpRequest, String> mapper) {
        expect(request -> {
            BasicClassicHttpResponse response = new BasicClassicHttpResponse(status);
            response.setEntity(HttpEntities.create(mapper.apply(request), ContentType.APPLICATION_JSON));
            return response;
        });
    }

    @FunctionalInterface
    public interface Expectation {

        ClassicHttpResponse handle(ClassicHttpRequest request) throws IOException;

    }

}
