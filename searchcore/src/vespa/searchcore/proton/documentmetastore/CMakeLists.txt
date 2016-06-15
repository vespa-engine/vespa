# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcore_documentmetastore STATIC
    SOURCES
    document_meta_store_explorer.cpp
    document_meta_store_initializer_result.cpp
    documentmetastore.cpp
    documentmetastoreattribute.cpp
    documentmetastorecontext.cpp
    documentmetastoreflushtarget.cpp
    documentmetastoreinitializer.cpp
    documentmetastoresaver.cpp
    search_context.cpp
    lid_allocator.cpp
    lid_gid_key_comparator.cpp
    lid_reuse_delayer_config.cpp
    lidreusedelayer.cpp
    lidstatevector.cpp
    DEPENDS
    searchcore_attribute
    searchcore_bucketdb
    searchcore_initializer
)
