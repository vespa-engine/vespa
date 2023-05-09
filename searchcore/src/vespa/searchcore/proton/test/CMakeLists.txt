# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcore_test STATIC
    SOURCES
    attribute_utils.cpp
    attribute_vectors.cpp
    bucketfactory.cpp
    buckethandler.cpp
    bucketstatecalculator.cpp
    clusterstatehandler.cpp
    documentdb_config_builder.cpp
    dummy_feed_view.cpp
    dummy_flush_target.cpp
    dummydbowner.cpp
    mock_gid_to_lid_change_handler.cpp
    mock_index_manager.cpp
    mock_shared_threading_service.cpp
    userdocumentsbuilder.cpp
    resulthandler.cpp
    threading_service_observer.cpp
    transport_helper.cpp
    DEPENDS
    searchcore_server
    searchcorespi
    searchlib_test
)
