# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_jar(container-disc-jar-with-dependencies.jar)

vespa_install_script(src/main/sh/vespa-start-container-daemon.sh vespa-start-container-daemon bin)

install_config_definitions()
