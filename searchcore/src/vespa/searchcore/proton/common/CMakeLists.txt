# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcore_pcommon STATIC
    SOURCES
    attributefieldvaluenode.cpp
    attrupdate.cpp
    bucketfactory.cpp
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
    schemautil.cpp
    selectpruner.cpp
    selectcontext.cpp
    state_reporter_utils.cpp
    DEPENDS
    searchcore_proton_metrics
)
