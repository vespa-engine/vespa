# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    fastos
    vespalog
    vespalib
    staging_vespalib
    fnet
    configdefinitions
    fastlib_fast
    document
    config_cloudconfig
    searchcommon
    persistencetypes
    metrics
    searchlib
    juniper

    LIBS
    src/vespa/searchsummary
    src/vespa/searchsummary/config
    src/vespa/searchsummary/docsummary
    src/vespa/searchsummary/test

    TESTS
    src/tests/docsumformat
    src/tests/docsummary
    src/tests/docsummary/attribute_combiner
    src/tests/docsummary/attributedfw
    src/tests/docsummary/matched_elements_filter
    src/tests/docsummary/slime_summary
    src/tests/extractkeywords
)
