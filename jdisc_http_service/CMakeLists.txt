# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_fat_java_artifact(jdisc_http_service)
install_java_artifact_dependencies(jdisc_http_service)

install_config_definition(src/main/resources/configdefinitions/jdisc.http.client.http-client.def)
install_config_definition(src/main/resources/configdefinitions/jdisc.http.connector.def)
install_config_definition(src/main/resources/configdefinitions/jdisc.http.server.def)
install_config_definition(src/main/resources/configdefinitions/jdisc.http.servlet-paths.def)

