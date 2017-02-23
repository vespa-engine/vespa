# Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcore_reference STATIC
    SOURCES
    document_db_reference_resolver.cpp
    document_db_referent.cpp
    document_db_referent_registry.cpp
    gid_to_lid_change_handler.cpp
    gid_to_lid_change_listener.cpp
    gid_to_lid_change_registrator.cpp
    gid_to_lid_mapper.cpp
    gid_to_lid_mapper_factory.cpp
    DEPENDS
    searchcore_attribute
    searchcore_documentmetastore
    searchcore_pcommon
)
