# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_fef_test OBJECT
    SOURCES
    attribute_map.cpp
    dummy_dependency_handler.cpp
    featuretest.cpp
    ftlib.cpp
    indexenvironment.cpp
    indexenvironmentbuilder.cpp
    labels.cpp
    matchdatabuilder.cpp
    mock_attribute_context.cpp
    queryenvironment.cpp
    queryenvironmentbuilder.cpp
    rankresult.cpp
    DEPENDS
)
