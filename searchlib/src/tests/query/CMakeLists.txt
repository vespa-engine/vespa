# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchlib_query_visitor_test_app TEST
    SOURCES
    query_visitor_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_query_visitor_test_app COMMAND searchlib_query_visitor_test_app)
vespa_add_executable(searchlib_customtypevisitor_test_app TEST
    SOURCES
    customtypevisitor_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_customtypevisitor_test_app COMMAND searchlib_customtypevisitor_test_app)
vespa_add_executable(searchlib_templatetermvisitor_test_app TEST
    SOURCES
    templatetermvisitor_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_templatetermvisitor_test_app COMMAND searchlib_templatetermvisitor_test_app)
vespa_add_executable(searchlib_querybuilder_test_app TEST
    SOURCES
    querybuilder_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_querybuilder_test_app COMMAND searchlib_querybuilder_test_app)
vespa_add_executable(searchlib_stackdumpquerycreator_test_app TEST
    SOURCES
    stackdumpquerycreator_test.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_stackdumpquerycreator_test_app COMMAND searchlib_stackdumpquerycreator_test_app)
vespa_add_executable(searchlib_query-old_test_app TEST
    SOURCES
    query-old.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_query-old_test_app COMMAND searchlib_query-old_test_app)
vespa_add_executable(searchlib_query-old-large_test_app TEST
    SOURCES
    query-old-large.cpp
    DEPENDS
    searchlib
)
vespa_add_test(NAME searchlib_query-old-large_test_app COMMAND searchlib_query-old-large_test_app)
