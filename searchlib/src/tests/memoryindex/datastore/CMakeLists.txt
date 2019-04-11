# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_feature_store_test_app TEST
    SOURCES
    feature_store_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_feature_store_test_app COMMAND searchlib_feature_store_test_app)
vespa_add_executable(searchlib_wordstore_test_app TEST
    SOURCES
    wordstore_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_wordstore_test_app COMMAND searchlib_wordstore_test_app)
