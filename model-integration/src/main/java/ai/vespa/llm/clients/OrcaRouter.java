// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.llm.clients;

import ai.vespa.secret.Secrets;
import com.yahoo.api.annotations.Beta;
import com.yahoo.component.annotation.Inject;

/**
 * A configurable client for the OrcaRouter OpenAI-compatible gateway
 * (https://www.orcarouter.ai) that extends the {@link OpenAICompatibleClient} class.
 * OrcaRouter exposes a provider/model namespace across many models, so a single
 * component can route completions to different models using the same endpoint.
 * Supports basic completion and structured JSON output, mirroring {@link OpenAI}.
 *
 * @author nissrin2020ali-ux
 */
@Beta
public class OrcaRouter extends OpenAICompatibleClient {
    private static final String DEFAULT_MODEL = "orcarouter/fusion-mini";
    private static final String DEFAULT_ENDPOINT = "https://api.orcarouter.ai/v1/";
    private static final String DEFAULT_API_KEY = "<YOUR_API_KEY>";

    @Inject
    public OrcaRouter(LlmClientConfig config, Secrets secretStore) {
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
