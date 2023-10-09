// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.classanalysis.sampleclasses;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Input for class analysis test verifying Java 14 switch statement.
 *
 * @author gjoranv
 */
public class SwitchStatement {

    void switchStatement() throws Exception{
        String foo = "";
        Collection<?> c = switch (foo) {
            case "list" -> List.of();
            case "set" -> Set.of();
            default -> throw new IllegalArgumentException();
        };
    }
}
