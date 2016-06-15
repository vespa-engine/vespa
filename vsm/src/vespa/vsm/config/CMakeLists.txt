# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vsm_vconfig OBJECT
    SOURCES
    DEPENDS
)
vespa_generate_config(vsm_vconfig vsmfields.def)
install(FILES vsmfields.def DESTINATION var/db/vespa/config_server/serverdb/classes)
vespa_generate_config(vsm_vconfig vsm.def)
install(FILES vsm.def DESTINATION var/db/vespa/config_server/serverdb/classes)
vespa_generate_config(vsm_vconfig vsmsummary.def)
install(FILES vsmsummary.def DESTINATION var/db/vespa/config_server/serverdb/classes)
