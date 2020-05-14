# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcore_pcommon STATIC
    SOURCES
    attribute_updater.cpp
    attributefieldvaluenode.cpp
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
    operation_rate_tracker.cpp
    select_utils.cpp
    selectcontext.cpp
    selectpruner.cpp
    state_reporter_utils.cpp
    statusreport.cpp
    transient_memory_usage_provider.cpp
    DEPENDS
    searchcore_proton_metrics
    searchcore_fconfig
    ${VESPA_STDCXX_FS_LIB}
)
