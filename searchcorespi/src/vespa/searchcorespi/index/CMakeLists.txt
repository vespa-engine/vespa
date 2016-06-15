# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcorespi_index OBJECT
    SOURCES
    activediskindexes.cpp
    diskindexcleaner.cpp
    eventlogger.cpp
    fusionrunner.cpp
    iindexmanager.cpp
    iindexcollection.cpp
    index_manager_explorer.cpp
    indexcollection.cpp
    indexdisklayout.cpp
    indexflushtarget.cpp
    indexfusiontarget.cpp
    indexmaintainer.cpp
    indexmaintainerconfig.cpp
    indexmaintainercontext.cpp
    indexmanagerconfig.cpp
    indexreadutilities.cpp
    indexsearchable.cpp
    indexwriteutilities.cpp
    warmupindexcollection.cpp
    isearchableindexcollection.cpp
    DEPENDS
)
