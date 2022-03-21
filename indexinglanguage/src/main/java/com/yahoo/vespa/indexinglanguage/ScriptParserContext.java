// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.language.Linguistics;
import com.yahoo.language.process.Embedder;
import com.yahoo.vespa.indexinglanguage.linguistics.AnnotatorConfig;
import com.yahoo.vespa.indexinglanguage.parser.CharStream;

import java.util.Collections;
import java.util.Map;

/**
 * @author Simon Thoresen Hult
 */
public class ScriptParserContext {

    private AnnotatorConfig annotatorConfig = new AnnotatorConfig();
    private Linguistics linguistics;
    private final Map<String, Embedder> embedders;
    private String defaultFieldName = null;
    private CharStream inputStream = null;

    public ScriptParserContext(Linguistics linguistics, Map<String, Embedder> embedders) {
        this.linguistics = linguistics;
        this.embedders = embedders;
    }

    public AnnotatorConfig getAnnotatorConfig() {
        return annotatorConfig;
    }

    public ScriptParserContext setAnnotatorConfig(AnnotatorConfig config) {
        annotatorConfig = new AnnotatorConfig(config);
        return this;
    }

    public Linguistics getLinguistcs() {
        return linguistics;
    }

    public ScriptParserContext setLinguistics(Linguistics linguistics) {
        this.linguistics = linguistics;
        return this;
    }

    public Map<String, Embedder> getEmbedders() {
        return Collections.unmodifiableMap(embedders);
    }

    public String getDefaultFieldName() {
        return defaultFieldName;
    }

    public ScriptParserContext setDefaultFieldName(String name) {
        defaultFieldName = name;
        return this;
    }

    public CharStream getInputStream() {
        return inputStream;
    }

    public ScriptParserContext setInputStream(CharStream stream) {
        inputStream = stream;
        return this;
    }

}
