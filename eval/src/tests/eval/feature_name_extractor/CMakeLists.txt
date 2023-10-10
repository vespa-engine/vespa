# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(eval_name_extractor_test_app TEST
    SOURCES
    feature_name_extractor_test.cpp
    DEPENDS
    vespaeval
)
vespa_add_test(NAME eval_name_extractor_test_app COMMAND eval_name_extractor_test_app)
