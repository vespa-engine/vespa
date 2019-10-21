// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.json;

/**
 * @author hakonhall
 */
public class WhitelistCondition extends ListCondition {
    public WhitelistCondition(CreateParams params) {
        super(Type.WHITELIST, params);
    }
}
