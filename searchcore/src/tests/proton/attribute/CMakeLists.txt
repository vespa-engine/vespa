# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchcore_attribute_test_app
    SOURCES
    attribute_test.cpp
    DEPENDS
    searchcore_server
    searchcore_attribute
    searchcore_flushengine
    searchcore_pcommon
)
vespa_add_test(NAME searchcore_attribute_test_app COMMAND sh attribute_test.sh)
vespa_add_executable(searchcore_attributeflush_test_app
    SOURCES
    attributeflush_test.cpp
    DEPENDS
    searchcore_server
    searchcore_attribute
    searchcore_flushengine
    searchcore_pcommon
)
vespa_add_test(NAME searchcore_attributeflush_test_app COMMAND sh attributeflush_test.sh)
