// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

public class LogctlSpec {
    public final String componentSpec;
    public final String levelsModSpec;
    public LogctlSpec(String componentSpec, String levelsModSpec) {
        this.componentSpec = componentSpec;
        this.levelsModSpec = levelsModSpec;
    }
}
