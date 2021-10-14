<!-- Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->

# Integration tests for JDisc components

This module contains integration tests for container components.

Tests that use the `application` framework cannot be added to the same maven
module as the component itself because that will usually create a cycle in the
dependency graph.
