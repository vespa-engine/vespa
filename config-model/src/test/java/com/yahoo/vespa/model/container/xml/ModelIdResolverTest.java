package com.yahoo.vespa.model.container.xml;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static com.yahoo.vespa.model.container.xml.ModelIdResolver.HF_TOKENIZER;
import static com.yahoo.vespa.model.container.xml.ModelIdResolver.ONNX_MODEL;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author bjorncs
 */
class ModelIdResolverTest {

    @Test
    void throws_on_known_model_with_missing_tags() {
        var state = new DeployState.Builder().properties(new TestProperties().setHostedVespa(true)).build();
        var e = assertThrows(IllegalArgumentException.class, () ->
                ModelIdResolver.resolveToModelReference(
                        "param", Optional.of("minilm-l6-v2"), Optional.empty(), Optional.empty(), Set.of(HF_TOKENIZER), state));
        var expectedMsg = "Model 'minilm-l6-v2' on 'param' has tags [onnx-model] but are missing required tags [huggingface-tokenizer]";
        assertEquals(expectedMsg, e.getMessage());

        assertDoesNotThrow(
                () -> ModelIdResolver.resolveToModelReference(
                        "param", Optional.of("minilm-l6-v2"), Optional.empty(), Optional.empty(), Set.of(ONNX_MODEL), state));
    }

}