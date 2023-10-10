# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(storage_storageconfig OBJECT
    SOURCES
    distributorconfiguration.cpp
    DEPENDS
)
vespa_generate_config(storage_storageconfig stor-communicationmanager.def)
install_config_definition(stor-communicationmanager.def vespa.config.content.core.stor-communicationmanager.def)
vespa_generate_config(storage_storageconfig stor-distributormanager.def)
install_config_definition(stor-distributormanager.def vespa.config.content.core.stor-distributormanager.def)
vespa_generate_config(storage_storageconfig stor-server.def)
install_config_definition(stor-server.def vespa.config.content.core.stor-server.def)
vespa_generate_config(storage_storageconfig stor-status.def)
install_config_definition(stor-status.def vespa.config.content.core.stor-status.def)
vespa_generate_config(storage_storageconfig stor-opslogger.def)
install_config_definition(stor-opslogger.def vespa.config.content.core.stor-opslogger.def)
vespa_generate_config(storage_storageconfig stor-visitordispatcher.def)
install_config_definition(stor-visitordispatcher.def vespa.config.content.core.stor-visitordispatcher.def)
vespa_generate_config(storage_storageconfig stor-bouncer.def)
install_config_definition(stor-bouncer.def vespa.config.content.core.stor-bouncer.def)
vespa_generate_config(storage_storageconfig stor-prioritymapping.def)
install_config_definition(stor-prioritymapping.def vespa.config.content.core.stor-prioritymapping.def)
vespa_generate_config(storage_storageconfig rpc-provider.def)
install_config_definition(rpc-provider.def vespa.config.content.core.rpc-provider.def)
