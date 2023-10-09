# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_vespalib_btree OBJECT
    SOURCES
    btree_key_data.cpp
    btreeaggregator.cpp
    btreebuilder.cpp
    btreeinserter.cpp
    btreeiterator.cpp
    btreenode.cpp
    btreenodeallocator.cpp
    btreenodestore.cpp
    btreeremover.cpp
    btreeroot.cpp
    btreerootbase.cpp
    btreestore.cpp
    DEPENDS
)
