# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    fastos
    vespalog
    vespalib

    LIBS
    src/vespa/fnet
    src/vespa/fnet/frt

    TESTS
    src/examples/frt/rpc
    src/examples/ping
    src/examples/timeout
    src/tests/connect
    src/tests/connection_spread
    src/tests/databuffer
    src/tests/examples
    src/tests/frt/detach_supervisor
    src/tests/frt/method_pt
    src/tests/frt/parallel_rpc
    src/tests/frt/rpc
    src/tests/frt/values
    src/tests/info
    src/tests/locking
    src/tests/printstuff
    src/tests/scheduling
    src/tests/sync_execute
    src/tests/thread_selection
    src/tests/time
    src/tests/transport_debugger
)
