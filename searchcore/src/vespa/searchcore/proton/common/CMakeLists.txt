# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcore_pcommon STATIC
    SOURCES
    attributefieldvaluenode.cpp
    attrupdate.cpp
    cachedselect.cpp
    commit_time_tracker.cpp
    dbdocumentid.cpp
    doctypename.cpp
    document_type_inspector.cpp
    eventlogger.cpp
    feeddebugger.cpp
    feedtoken.cpp
    hw_info_sampler.cpp
    indexschema_inspector.cpp
    monitored_refcount.cpp
    select_utils.cpp
    selectpruner.cpp
    selectcontext.cpp
    state_reporter_utils.cpp
    statusreport.cpp
    DEPENDS
    searchcore_proton_metrics
    searchcore_fconfig
    stdc++fs
)
