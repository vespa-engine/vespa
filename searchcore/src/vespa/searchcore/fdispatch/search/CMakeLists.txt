# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcore_fdispatch_search STATIC
    SOURCES
    configdesc.cpp
    dataset_base.cpp
    datasetcollection.cpp
    engine_base.cpp
    fnet_dataset.cpp
    fnet_engine.cpp
    fnet_search.cpp
    mergehits.cpp
    nodemanager.cpp
    plain_dataset.cpp
    query.cpp
    querycacheutil.cpp
    rowstate.cpp
    search_path.cpp
    DEPENDS
    searchcore_fconfig
    searchcore_util
)
