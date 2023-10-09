# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(fnet_drainpackets_test_app TEST
    SOURCES
    drainpackets.cpp
    DEPENDS
    fnet
)
vespa_add_test(NAME fnet_drainpackets_test_app NO_VALGRIND COMMAND fnet_drainpackets_test_app)
vespa_add_executable(fnet_lockspeed_test_app TEST
    SOURCES
    lockspeed.cpp
    dummy.cpp
    DEPENDS
    fnet
)
vespa_add_test(NAME fnet_lockspeed_test_app NO_VALGRIND COMMAND fnet_lockspeed_test_app)
vespa_add_executable(fnet_castspeed_test_app TEST
    SOURCES
    castspeed.cpp
    DEPENDS
    fnet
)
vespa_add_test(NAME fnet_castspeed_test_app NO_VALGRIND COMMAND fnet_castspeed_test_app)
