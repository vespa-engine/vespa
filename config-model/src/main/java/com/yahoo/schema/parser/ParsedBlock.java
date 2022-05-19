// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

/**
 * Common methods for various Parsed* classes.
 * @author arnej27959
 **/
public class ParsedBlock {
    private final String name;
    private final String blockType;

    public ParsedBlock(String name, String blockType) {
        this.name = name;
        this.blockType = blockType;
    }

    public final String name() { return name; }
    public final String blockType() { return blockType; }

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

    public String toString() {
        return blockType + " '" + name + "'";
    }
}

