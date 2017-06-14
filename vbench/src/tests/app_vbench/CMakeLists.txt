# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vbench_app_vbench_test_app TEST
    SOURCES
    app_vbench_test.cpp
    DEPENDS
    vbench_test
    vbench
)
vespa_add_test(NAME vbench_app_vbench_test_app NO_VALGRIND COMMAND vbench_app_vbench_test_app)
configure_file(vbench.cfg.template vbench.cfg @ONLY)
