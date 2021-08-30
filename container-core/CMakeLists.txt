# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_config_definitions()
install_java_artifact_dependencies(container-core)

vespa_install_script(src/main/sh/vespa-load-balancer-status libexec/vespa)
vespa_install_script(src/main/sh/vespa-jvm-dumper bin)
