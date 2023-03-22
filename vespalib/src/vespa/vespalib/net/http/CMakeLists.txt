# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib_vespalib_net_http OBJECT
    SOURCES
    component_config_producer.cpp
    generic_state_handler.cpp
    http_server.cpp
    json_get_handler.cpp
    json_handler_repo.cpp
    simple_component_config_producer.cpp
    simple_health_producer.cpp
    simple_metric_snapshot.cpp
    simple_metrics_producer.cpp
    slime_explorer.cpp
    state_api.cpp
    state_explorer.cpp
    state_server.cpp
    DEPENDS
)
