// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage;

import com.yahoo.language.Linguistics;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.vespa.indexinglanguage.linguistics.AnnotatorConfig;
import com.yahoo.vespa.indexinglanguage.parser.CharStream;

/**
 * @author Simon Thoresen Hult
 */
public class ScriptParserContext {

    private AnnotatorConfig annotatorConfig = new AnnotatorConfig();
    private Linguistics linguistics;
    private String defaultFieldName = null;
    private CharStream inputStream = null;

    public ScriptParserContext(Linguistics linguistics) {
        this.linguistics = linguistics;
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
