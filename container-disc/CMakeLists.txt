# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_fat_java_artifact(container-disc)

vespa_install_script(src/main/sh/vespa-start-container-daemon.sh vespa-start-container-daemon bin)

install_config_definition(src/main/resources/configdefinitions/jdisc-bindings.def container.jdisc.jdisc-bindings.def)
install_config_definition(src/main/resources/configdefinitions/jersey-connection.def container.config.jersey.jersey-connection.def)
install_config_definition(src/main/resources/configdefinitions/jersey-web-app-pool.def container.config.jersey.jersey-web-app-pool.def)
