// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.json;

/**
 * @author hakonhall
 */
public class WhitelistCondition extends ListCondition {
    public static WhitelistCondition create(CreateParams params) { return new WhitelistCondition(params); }
    private WhitelistCondition(CreateParams params) { super(Type.WHITELIST, params); }
}
