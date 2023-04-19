# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    vespalog
    storage
    config_cloudconfig
    vespalib
    document
    vdslib
    searchlib
    searchsummary

    LIBS
    src/vespa/searchvisitor
    src/vespa/vsm/common
    src/vespa/vsm/config
    src/vespa/vsm/searcher
    src/vespa/vsm/vsm

    TESTS
    src/tests/charbuffer
    src/tests/docsum
    src/tests/document
    src/tests/hitcollector
    src/tests/matching_elements_filler
    src/tests/nearest_neighbor_field_searcher
    src/tests/query_term_filter_factory
    src/tests/querywrapper
    src/tests/rank_processor
    src/tests/searcher
    src/tests/searchvisitor
    src/tests/textutil
)
