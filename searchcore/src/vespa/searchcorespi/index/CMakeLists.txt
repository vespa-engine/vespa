# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcorespi_index STATIC OBJECT
    SOURCES
    diskindexcleaner.cpp
    disk_indexes.cpp
    disk_index_stats.cpp
    eventlogger.cpp
    fusionrunner.cpp
    iindexmanager.cpp
    iindexcollection.cpp
    index_disk_dir_state.cpp
    index_manager_explorer.cpp
    index_manager_stats.cpp
    indexcollection.cpp
    indexdisklayout.cpp
    indexflushtarget.cpp
    indexfusiontarget.cpp
    indexmaintainer.cpp
    indexmaintainerconfig.cpp
    indexmaintainercontext.cpp
    indexmanagerconfig.cpp
    indexreadutilities.cpp
    index_searchable_stats.cpp
    indexwriteutilities.cpp
    warmupindexcollection.cpp
    isearchableindexcollection.cpp
    DEPENDS
)
