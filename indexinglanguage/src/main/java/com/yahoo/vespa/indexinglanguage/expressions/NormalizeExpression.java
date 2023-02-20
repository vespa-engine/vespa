// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.expressions;

import com.yahoo.document.DataType;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.Transformer;

import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * @author Simon Thoresen Hult
 */
public final class NormalizeExpression extends Expression {

    private final Linguistics linguistics;
    private static final Logger logger = Logger.getLogger(NormalizeExpression.class.getName());

    public NormalizeExpression(Linguistics linguistics) {
        super(DataType.STRING);
        this.linguistics = linguistics;
    }

    public Linguistics getLinguistics() {
        return linguistics;
    }
    
    private static String escape(String str) {
        StringBuilder buf = new StringBuilder();
        for (char c : str.toCharArray()) {
            if (c >= ' ') {
                buf.append(c);
            } else {
                buf.append(String.format("U+%04X", (int)c));
            }
        }
        return buf.toString();
    }

    @Override
    protected void doExecute(ExecutionContext context) {
        Transformer transformer = linguistics.getTransformer();
        var orig = String.valueOf(context.getValue());
        if (orig.isEmpty()) {
            return; // must be a no-op for all linguistics/language combinations
        }
        var lang = context.resolveLanguage(linguistics);
        var transformed = transformer.accentDrop(orig, lang);
        try {
            context.setValue(new StringFieldValue(transformed));
            return;
        } catch (IllegalArgumentException ex) {
            String msg = ("bad normalize, \n" +
                          "original: >>> " + escape(orig) + " <<<\n" +
                          " -> accentDrop(" + lang + ") -> \n" +
                          "transformed: >>> " + escape(transformed) + " <<<");
            logger.log(Level.SEVERE, msg);
        }
        context.setValue(new StringFieldValue(transformer.accentDrop(String.valueOf(context.getValue()),
                                                                     context.resolveLanguage(linguistics))));
    }

    @Override
    protected void doVerify(VerificationContext context) {
        context.setValueType(createdOutputType());
    }

    @Override
    public DataType createdOutputType() {
        return DataType.STRING;
    }

    @Override
    public String toString() {
        return "normalize";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof NormalizeExpression other)) return false;
        if (linguistics.getClass() != other.linguistics.getClass()) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
