// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author bjorncs
 */
class SecretsProviderTest {

    @Test
    void converts_camelCase_to_env_name() {
        assertEquals("VESPA_SECRET_VOYAGE_API_KEY", SecretsProvider.EnvironmentSecrets.toEnvName("voyageApiKey"));
        assertEquals("VESPA_SECRET_OPEN_AI_API_KEY", SecretsProvider.EnvironmentSecrets.toEnvName("openAiApiKey"));
        assertEquals("VESPA_SECRET_SECRET", SecretsProvider.EnvironmentSecrets.toEnvName("secret"));
        assertEquals("VESPA_SECRET_NAME", SecretsProvider.EnvironmentSecrets.toEnvName("NAME"));
        assertEquals("VESPA_SECRET_OPEN_AIKEY", SecretsProvider.EnvironmentSecrets.toEnvName("openAIKey"));
    }

    @Test
    void converts_snake_case_to_env_name() {
        assertEquals("VESPA_SECRET_VOYAGE_API_KEY", SecretsProvider.EnvironmentSecrets.toEnvName("voyage_api_key"));
        assertEquals("VESPA_SECRET_OPEN_AI_API_KEY", SecretsProvider.EnvironmentSecrets.toEnvName("open_ai_api_key"));
        assertEquals("VESPA_SECRET_OPENAI_API_KEY", SecretsProvider.EnvironmentSecrets.toEnvName("OPENAI_API_KEY"));
    }

    @Test
    void throws_on_missing_env_var() {
        var provider = new SecretsProvider();
        var secrets = provider.get();
        var exception = assertThrows(IllegalArgumentException.class, () -> secrets.get("nonExistentKey"));
        assertEquals("Secret 'nonExistentKey' not found. Set environment variable 'VESPA_SECRET_NON_EXISTENT_KEY'",
                     exception.getMessage());
    }

}
