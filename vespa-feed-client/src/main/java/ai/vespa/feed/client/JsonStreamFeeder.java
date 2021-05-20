// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import com.fasterxml.jackson.core.JsonParser;

import java.io.InputStream;
import java.time.Duration;

import static java.util.Objects.requireNonNull;

/**
 * @author jonmv
 */
public class JsonStreamFeeder {

    private final FeedClient client;
    private final OperationParameters protoParameters;

    private JsonStreamFeeder(FeedClient client, OperationParameters protoParameters) {
        this.client = client;
        this.protoParameters = protoParameters;
    }

    public static Builder builder(FeedClient client) { return new Builder(client); }

    /** Feeds a stream containing a JSON array of feed operations on the form
     * <pre>
     *     [
     *       {
     *         "id": "id:ns:type::boo",
     *         "fields": { ... document fields ... }
     *       },
     *       {
     *         "put": "id:ns:type::foo",
     *         "fields": { ... document fields ... }
     *       },
     *       {
     *         "update": "id:ns:type:n=4:bar",
     *         "create": true,
     *         "fields": { ... partial update fields ... }
     *       },
     *       {
     *         "remove": "id:ns:type:g=foo:bar",
     *         "condition": "type.baz = \"bax\""
     *       },
     *       ...
     *     ]
     * </pre>
     * Note that {@code "id"} is an alias for the document put operation.
     */
    public void feed(InputStream jsonStream) {

    }


    static class Tokenizer {

        private final InputStream in;
        private final JsonParser json;

        public Tokenizer(InputStream in, JsonParser json) {
            this.in = in;
            this.json = json;
        }

    }



    public static class Builder {

        final FeedClient client;
        OperationParameters parameters;

        private Builder(FeedClient client) {
            this.client = requireNonNull(client);
        }

        public Builder withTimeout(Duration timeout) {
            parameters = parameters.timeout(timeout);
            return this;
        }

        public Builder withRoute(String route) {
            parameters = parameters.route(route);
            return this;
        }

        public Builder withTracelevel(int tracelevel) {
            parameters = parameters.tracelevel(tracelevel);
            return this;
        }

        public JsonStreamFeeder build() {
            return new JsonStreamFeeder(client, parameters);
        }

    }

}
