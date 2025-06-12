// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.config.UrlReference;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.text.XML;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author hmusum
 */
public class ModelTest {

    @Test
    void invalid_url(){
        var xml = """
                <component id="bert-embedder" type="bert-embedder">
                  <transformer-model url="models/e5-base-v2.onnx" />
                  <tokenizer-vocab path="models/vocab.txt"/>
                </component>
                """;

        var state = new DeployState.Builder().build();
        var element = XML.getDocument(xml).getDocumentElement();
        var exception = assertThrows(IllegalArgumentException.class,
                     () -> Model.fromXml(state, element, "transformer-model", Set.of()));
        assertEquals("Invalid url 'models/e5-base-v2.onnx': url has no 'scheme' component", exception.getMessage());
    }

    @Test
    void valid_url(){
        var dummyUrl = "https://vespa.ai/some-model.onxx";
        var xml = """
                <component id="bert-embedder" type="hugging-face-embedder">
                  <transformer-model url="%s" />
                  <tokenizer-model url="%s"/>
                </component>
                """.formatted(dummyUrl, dummyUrl);

        var state = new DeployState.Builder().build();
        var element = XML.getDocument(xml).getDocumentElement();

        var model = Model.fromXml(state, element, "transformer-model", Set.of()).get();
        assertEquals(Optional.of(UrlReference.valueOf(dummyUrl)), model.modelReference().url());

        var tokenizer = Model.fromXml(state, element, "tokenizer-model", Set.of()).get();
        assertEquals(Optional.of(UrlReference.valueOf(dummyUrl)), tokenizer.modelReference().url());
    }

    @Test
    void authenticated_url(){
        var dummyUrl = "https://vespa.ai/some-model.onxx";
        var xml = """
                <component id="bert-embedder" type="hugging-face-embedder">
                  <transformer-model url="%s" secret-ref="myTransformerSecret" />
                  <tokenizer-model url="%s" secret-ref="myTokenizerSecret"/>
                </component>
                """.formatted(dummyUrl, dummyUrl);

        var state = new DeployState.Builder().build();
        var element = XML.getDocument(xml).getDocumentElement();

        var model = Model.fromXml(state, element, "transformer-model", Set.of()).get();
        assertEquals(Optional.of("myTransformerSecret"), model.modelReference().secretRef());
        assertEquals(Optional.of(UrlReference.valueOf(dummyUrl)), model.modelReference().url());

        var tokenizer = Model.fromXml(state, element, "tokenizer-model", Set.of()).get();
        assertEquals(Optional.of("myTokenizerSecret"), tokenizer.modelReference().secretRef());
        assertEquals(Optional.of(UrlReference.valueOf(dummyUrl)), tokenizer.modelReference().url());

    }

}
