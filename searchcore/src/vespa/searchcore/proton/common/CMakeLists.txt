# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcore_pcommon STATIC
    SOURCES
    alloc_config.cpp
    alloc_strategy.cpp
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
    ipendinglidtracker.cpp
    monitored_refcount.cpp
    operation_rate_tracker.cpp
    pendinglidtracker.cpp
    select_utils.cpp
    selectcontext.cpp
    selectpruner.cpp
    state_reporter_utils.cpp
    statusreport.cpp
    transient_memory_usage_provider.cpp
    DEPENDS
    searchcore_proton_metrics
    searchcore_fconfig
    EXTERNAL_DEPENDS
    ${VESPA_STDCXX_FS_LIB}
)

if(VESPA_OS_DISTRO_COMBINED STREQUAL "rhel 8.2" OR VESPA_OS_DISTRO_COMBINED STREQUAL "centos 8")
  set_source_files_properties(hw_info_sampler.cpp PROPERTIES COMPILE_FLAGS -DRHEL_8_2_KLUDGE)
endif()
