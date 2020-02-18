# Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_fat_java_artifact(metrics-proxy)

install_config_definition(src/main/resources/configdefinitions/application-dimensions.def ai.vespa.metricsproxy.metric.dimensions.application-dimensions.def)
install_config_definition(src/main/resources/configdefinitions/consumers.def ai.vespa.metricsproxy.core.consumers.def)
install_config_definition(src/main/resources/configdefinitions/monitoring.def ai.vespa.metricsproxy.core.monitoring.def)
install_config_definition(src/main/resources/configdefinitions/metrics-nodes.def ai.vespa.metricsproxy.http.application.metrics-nodes.def)
install_config_definition(src/main/resources/configdefinitions/node-dimensions.def ai.vespa.metricsproxy.metric.dimensions.node-dimensions.def)
install_config_definition(src/main/resources/configdefinitions/node-info.def ai.vespa.metricsproxy.http.metrics.node-info.def)
install_config_definition(src/main/resources/configdefinitions/rpc-connector.def ai.vespa.metricsproxy.rpc.rpc-connector.def)
install_config_definition(src/main/resources/configdefinitions/vespa-services.def ai.vespa.metricsproxy.service.vespa-services.def)
install_config_definition(src/main/resources/configdefinitions/telegraf.def ai.vespa.metricsproxy.telegraf.telegraf.def)
