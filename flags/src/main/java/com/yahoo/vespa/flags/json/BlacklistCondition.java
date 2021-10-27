// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.json;

/**
 * @author hakonhall
 */
public class BlacklistCondition extends ListCondition {
    public static  BlacklistCondition create(CreateParams params) { return new BlacklistCondition(params); }
    private BlacklistCondition(CreateParams params) { super(Type.BLACKLIST, params); }
}
