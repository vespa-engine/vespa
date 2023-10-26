# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(document_documentconfig OBJECT
    SOURCES
    DEPENDS
)
vespa_generate_config(document_documentconfig documenttypes.def)
install_config_definition(documenttypes.def document.documenttypes.def)
install_config_definition(documentmanager.def document.config.documentmanager.def)
