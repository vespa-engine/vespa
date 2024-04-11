// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.text.XML;
import org.junit.jupiter.api.Test;

import java.util.Set;

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
org.junit.jupiter.api.Assertions.assertEquals("Invalid url 'models/e5-base-v2.onnx': url has no 'scheme' component", exception.getMessage());                   
    }

}
