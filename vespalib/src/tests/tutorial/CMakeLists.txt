# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(vespalib_make_tutorial_app TEST
    SOURCES
    make_tutorial.cpp
    DEPENDS
    vespalib
)
vespa_add_test(NAME vespalib_make_tutorial_app COMMAND ${CMAKE_CURRENT_SOURCE_DIR}/compare-tutorials.sh
               DEPENDS vespalib_make_tutorial_app)
vespa_add_executable(vespalib_xml_escape_app
    SOURCES
    xml_escape.cpp
    DEPENDS
    vespalib
)
