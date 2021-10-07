# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(searchsummary_docsum-pack_app TEST
    SOURCES
    docsum-pack.cpp
    DEPENDS
    searchsummary
)
vespa_add_test(NAME searchsummary_docsum-pack_app COMMAND searchsummary_docsum-pack_app)
