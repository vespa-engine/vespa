# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_jar(metrics-proxy-jar-with-dependencies.jar)

install(DIRECTORY DESTINATION conf/telegraf)
install(DIRECTORY DESTINATION logs/telegraf)
vespa_install_script(src/main/sh/start-telegraf.sh libexec/vespa)
vespa_install_script(src/main/sh/stop-telegraf.sh libexec/vespa)

install_config_definitions()
