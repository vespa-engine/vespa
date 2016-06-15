# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_attributeguard_test_app
    SOURCES
    attributeguard.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_attributeguard_test_app COMMAND sh attributeguard_test.sh)
vespa_add_executable(searchlib_attribute_test_app
    SOURCES
    attribute_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_attribute_test_app COMMAND sh attribute_test.sh)
vespa_add_executable(searchlib_changevector_test_app
    SOURCES
    changevector_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_changevector_test_app COMMAND sh changevector_test.sh)
vespa_add_executable(searchlib_attributebenchmark_app
    SOURCES
    attributebenchmark.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_attributebenchmark_app COMMAND searchlib_attributebenchmark_app BENCHMARK)
