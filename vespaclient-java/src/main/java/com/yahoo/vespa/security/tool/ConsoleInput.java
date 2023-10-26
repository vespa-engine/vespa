// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.security.tool;

/**
 * @author vekterli
 */
@FunctionalInterface
public interface ConsoleInput {

    String readPassword(String fmtPrompt, Object... fmtArgs);

}
