# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(slobrok_oldapi_test_app TEST
    SOURCES
    old.cpp
    mirror.cpp
    DEPENDS
    slobrok_slobrokserver
)
vespa_add_test(NAME slobrok_oldapi_test_app COMMAND slobrok_oldapi_test_app)
