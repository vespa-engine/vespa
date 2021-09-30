# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_child_process_test_app TEST
    SOURCES
    child_process_test.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_child_process_test_app COMMAND vespalib_child_process_test_app COST 30)
