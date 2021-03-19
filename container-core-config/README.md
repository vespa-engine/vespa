<!-- Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->
# container-core-config

Contains config definitions with package `com.yahoo.container.core` that are used by other modules.

This artifact is embedded inside container-core jar, but built as bundle to allow other modules to depend on container-core config definitions without depending on container-core.
The generated config classes cannot be moved to container-core as it would introduce a cycles in Maven dependency graph.
This works at correctly runtime as OSGi allows cycling dependencies between bundles.
