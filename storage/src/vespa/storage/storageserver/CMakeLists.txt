# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

vespa_add_library(storage_storageserver OBJECT
    SOURCES
    bouncer.cpp
    bouncer_metrics.cpp
    changedbucketownershiphandler.cpp
    communicationmanager.cpp
    communicationmanagermetrics.cpp
    config_logging.cpp
    configurable_bucket_resolver.cpp
    distributornode.cpp
    distributornodecontext.cpp
    documentapiconverter.cpp
    fnet_metrics_wrapper.cpp
    mergethrottler.cpp
    opslogger.cpp
    priorityconverter.cpp
    rpcrequestwrapper.cpp
    service_layer_error_listener.cpp
    servicelayernode.cpp
    servicelayernodecontext.cpp
    statemanager.cpp
    statereporter.cpp
    storagemetricsset.cpp
    storagenode.cpp
    storagenodecontext.cpp
    tls_statistics_metrics_wrapper.cpp
    $<TARGET_OBJECTS:storage_storageserver_rpc>
    DEPENDS
)
