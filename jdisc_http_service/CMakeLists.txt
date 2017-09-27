# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_fat_java_artifact(jdisc_http_service)

install(FILES 
        src/main/resources/configdefinitions/jdisc.http.client.http-client.def
        src/main/resources/configdefinitions/jdisc.http.connector.def
        src/main/resources/configdefinitions/jdisc.http.server.def
        src/main/resources/configdefinitions/jdisc.http.servlet-paths.def
        DESTINATION var/db/vespa/config_server/serverdb/classes)

