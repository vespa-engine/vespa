# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vbench_app_vbench_test_app TEST
    SOURCES
    app_vbench_test.cpp
    DEPENDS
    vbench_test
    vbench
)
vespa_add_test(NAME vbench_app_vbench_test_app NO_VALGRIND COMMAND vbench_app_vbench_test_app)
configure_file(vbench.cfg.template.template vbench.cfg.template @ONLY)
configure_file(vbench.tls.cfg.template.template vbench.tls.cfg.template @ONLY)
