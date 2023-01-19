// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.StemMode;
import com.yahoo.vespa.indexinglanguage.linguistics.AnnotatorConfig;
import com.yahoo.vespa.indexinglanguage.linguistics.LinguisticsAnnotator;

/**
 * @author Simon Thoresen Hult
 */
public final class TokenizeExpression extends Expression {

    private final Linguistics linguistics;
    private final AnnotatorConfig config;

    public TokenizeExpression(Linguistics linguistics, AnnotatorConfig config) {
        super(DataType.STRING);
        this.linguistics = linguistics;
        this.config = config;
    }

    public Linguistics getLinguistics() {
        return linguistics;
    }

    public AnnotatorConfig getConfig() {
        return config;
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        StringFieldValue input = (StringFieldValue)context.getValue();
        StringFieldValue output = input.clone();
        context.setValue(output);

        AnnotatorConfig cfg = new AnnotatorConfig(config);
        Language lang = context.resolveLanguage(linguistics);
        if (lang != null) {
            cfg.setLanguage(lang);
        }
        LinguisticsAnnotator annotator = new LinguisticsAnnotator(linguistics, cfg);
        annotator.annotate(output);
    }

    @Override
    protected void doVerify(VerificationContext context) {
        // empty
    }

    @Override
    public DataType createdOutputType() {
        return null;
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder();
        ret.append("tokenize");
        if (config.getRemoveAccents()) {
            ret.append(" normalize");
        }
        if (config.getStemMode() != StemMode.NONE) {
            ret.append(" stem:\""+config.getStemMode()+"\"");
        }
        if (config.hasNonDefaultMaxTokenLength()) {
            ret.append(" max-length:" + config.getMaxTokenizeLength());
        }
        return ret.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TokenizeExpression rhs)) return false;
        if (!config.equals(rhs.config)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode() + config.hashCode();
    }

}
