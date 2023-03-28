// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

import com.yahoo.container.logging.LevelsModSpec;

public record LogctlSpec(String componentSpec, LevelsModSpec levelsModSpec) {
}
