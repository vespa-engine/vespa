<!-- Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root. -->
# Container-test

Convenience dependency for testing plugin components for the Jdisc container
with the `application` test tool. This artifact should contain all libraries 
that are used internally by Vespa. Add this maven artifact as a **test** scope 
dependency in your pom.xml to transitively pull in all dependencies needed to unit test
JDisc components.

This should always be used in conjunction with a `provided` scoped dependency
on the `container` artifact, which contains all internal and 3rd party artifacts
that are exposed in Vespa's public APIs. (Internal Vespa developers should instead
use the `container-dev` artifact.)
