# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_btree OBJECT
    SOURCES
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
