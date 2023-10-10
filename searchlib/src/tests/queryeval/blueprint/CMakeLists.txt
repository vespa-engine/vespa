# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_blueprint_test_app TEST
    SOURCES
    blueprint_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_blueprint_test_app COMMAND searchlib_blueprint_test_app || diff -u lhs.out rhs.out)
vespa_add_executable(searchlib_leaf_blueprints_test_app TEST
    SOURCES
    leaf_blueprints_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_leaf_blueprints_test_app COMMAND searchlib_leaf_blueprints_test_app || diff -u lhs.out rhs.out)
vespa_add_executable(searchlib_intermediate_blueprints_test_app TEST
    SOURCES
    intermediate_blueprints_test.cpp
    DEPENDS
    searchlib
    searchlib_test
)
vespa_add_test(NAME searchlib_intermediate_blueprints_test_app COMMAND searchlib_intermediate_blueprints_test_app || diff -u lhs.out rhs.out)
