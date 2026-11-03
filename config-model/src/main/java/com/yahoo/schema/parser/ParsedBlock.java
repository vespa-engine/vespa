// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

import java.util.Optional;

/**
 * Common methods for various Parsed* classes.
 *
 * @author arnej27959
 */
public class ParsedBlock {

    private final String name;
    private final String blockType;
    private String explanation = null;

    public ParsedBlock(String name, String blockType) {
        this.name = name;
        this.blockType = blockType;
    }

    public final String name() { return name; }
    public final String blockType() { return blockType; }

    /** Returns the free text explanation of the content and usage of this, if any. */
    public final Optional<String> getExplanation() { return Optional.ofNullable(explanation); }

    public void setExplanation(String explanation) {
        verifyThat(this.explanation == null, "duplicate explanation");
        this.explanation = explanation;
    }

    protected void verifyThat(boolean check, String msg, Object ... msgDetails) {
        if (check) return;
        var buf = new StringBuilder();
        buf.append(blockType).append(" '").append(name).append("' error: ");
        buf.append(msg);
        for (Object detail : msgDetails) {
            buf.append(" ");
            buf.append(detail.toString());
        }
        throw new IllegalArgumentException(buf.toString());
    }

    @Override
    public String toString() {
        return blockType + " '" + name + "'";
    }

}

