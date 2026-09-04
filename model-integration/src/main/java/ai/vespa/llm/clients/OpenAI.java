// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.clients;

import ai.vespa.secret.Secrets;
import com.yahoo.api.annotations.Beta;
import com.yahoo.component.annotation.Inject;

/**
 * A configurable OpenAI client that extends the {@link OpenAICompatibleClient} class.
 * Uses the official OpenAI java client (https://github.com/openai/openai-java).
 * Supports basic completion and structured JSON output.
 *
 * @author lesters
 * @author glebashnik
 * @author thomasht86
 */
@Beta
public class OpenAI extends OpenAICompatibleClient {
    private static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/";
    private static final String DEFAULT_API_KEY = "<YOUR_API_KEY>";

    @Inject
    public OpenAI(LlmClientConfig config, Secrets secretStore) {
        super(config, secretStore);
    }

    @Override
    protected String defaultModel() {
        return DEFAULT_MODEL;
    }

    @Override
    protected String defaultEndpoint() {
        return DEFAULT_ENDPOINT;
    }

    @Override
    protected String defaultApiKey() {
        return DEFAULT_API_KEY;
    }
}
