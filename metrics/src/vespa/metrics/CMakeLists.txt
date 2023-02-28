# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(metrics
    SOURCES
    countmetric.cpp
    countmetricvalues.cpp
    jsonwriter.cpp
    memoryconsumption.cpp
    metric.cpp
    metricmanager.cpp
    metricset.cpp
    metricsnapshot.cpp
    metrictimer.cpp
    metricvalueset.cpp
    name_repo.cpp
    state_api_adapter.cpp
    summetric.cpp
    textwriter.cpp
    updatehook.cpp
    valuemetric.cpp
    valuemetricvalues.cpp
    $<TARGET_OBJECTS:metrics_common>

    INSTALL lib64
    DEPENDS
)
vespa_generate_config(metrics metricsmanager.def)
install_config_definition(metricsmanager.def metrics.metricsmanager.def)
