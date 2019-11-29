# Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
install_fat_java_artifact(jdisc-security-filters)

install_config_definition(src/main/resources/configdefinitions/cors-filter.def jdisc.http.filter.security.cors.cors-filter.def)
