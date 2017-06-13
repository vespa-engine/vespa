# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(document_documentconfig OBJECT
    SOURCES
    DEPENDS
)
vespa_generate_config(document_documentconfig documenttypes.def)
install(FILES
    documenttypes.def
    documentmanager.def
    DESTINATION var/db/vespa/config_server/serverdb/classes)
