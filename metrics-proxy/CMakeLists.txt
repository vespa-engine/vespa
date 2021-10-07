# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_fat_java_artifact(metrics-proxy)

vespa_install_script(src/main/sh/start-telegraf.sh libexec/vespa)
vespa_install_script(src/main/sh/stop-telegraf.sh libexec/vespa)

install_config_definitions()
