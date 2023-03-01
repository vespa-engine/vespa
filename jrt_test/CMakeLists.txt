# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    vespalog
    vespalib
    fnet
    slobrok
    configdefinitions

    APPS
    src/binref
    src/java
    src/jrt-test/simpleserver

    TESTS
    src/tests/connect-close
    src/tests/echo
    src/tests/garbage
    src/tests/hello-world
    src/tests/mandatory-methods
    src/tests/mockup-invoke
    src/tests/rpc-error
    src/tests/slobrok-api
)
