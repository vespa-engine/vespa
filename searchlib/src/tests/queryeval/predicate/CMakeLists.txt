# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_predicate_blueprint_test_app TEST
    SOURCES
    predicate_blueprint_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_predicate_blueprint_test_app COMMAND searchlib_predicate_blueprint_test_app)
vespa_add_executable(searchlib_predicate_search_test_app TEST
    SOURCES
    predicate_search_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_predicate_search_test_app COMMAND searchlib_predicate_search_test_app)
