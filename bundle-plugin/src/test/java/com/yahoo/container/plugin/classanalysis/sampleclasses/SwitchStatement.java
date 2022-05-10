package com.yahoo.container.plugin.classanalysis.sampleclasses;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.Collection;

/**
 * Input for class analysis test verifying Java 14 switch statement.
 *
 * @author gjoranv
 */
public class SwitchStatement {

    void switchStatement() throws Exception{
        String foo = "";
        Collection<?> c = switch (foo) {
            case "list" -> ImmutableList.of();
            case "set" -> ImmutableSet.of();
            default -> throw new IllegalArgumentException();
        };
    }
}
