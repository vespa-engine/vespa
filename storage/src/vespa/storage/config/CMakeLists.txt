# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_storageconfig OBJECT
    SOURCES
    distributorconfiguration.cpp
    DEPENDS
)
vespa_generate_config(storage_storageconfig vespa.config.content.core.stor-communicationmanager.def)
vespa_generate_config(storage_storageconfig vespa.config.content.core.stor-distributormanager.def)
vespa_generate_config(storage_storageconfig vespa.config.content.core.stor-server.def)
vespa_generate_config(storage_storageconfig vespa.config.content.core.stor-status.def)
vespa_generate_config(storage_storageconfig vespa.config.content.core.stor-messageforwarder.def)
vespa_generate_config(storage_storageconfig vespa.config.content.core.stor-opslogger.def)
vespa_generate_config(storage_storageconfig vespa.config.content.core.stor-visitordispatcher.def)
vespa_generate_config(storage_storageconfig vespa.config.content.core.stor-integritychecker.def)
vespa_generate_config(storage_storageconfig vespa.config.content.core.stor-bucketmover.def)
vespa_generate_config(storage_storageconfig vespa.config.content.core.stor-bouncer.def)
vespa_generate_config(storage_storageconfig vespa.config.content.core.stor-prioritymapping.def)
vespa_generate_config(storage_storageconfig vespa.config.content.core.rpc-provider.def)

install_config_definitions(src/vespa/storage/config)
