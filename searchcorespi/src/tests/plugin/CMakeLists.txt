# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcorespi_tplugin
        SOURCES
        plugin.cpp
        DEPENDS
        searchcorespi
)
vespa_add_library(searchcorespi_illegal-plugin
        SOURCES
        empty.cpp
        DEPENDS
        searchcorespi
)
vespa_add_executable(searchcorespi_plugin_test_app TEST
    SOURCES
    plugin_test.cpp
    DEPENDS
    searchcorespi
    AFTER
    searchcorespi_tplugin
    searchcorespi_illegal-plugin
)
vespa_add_test(
    NAME searchcorespi_plugin_test_app
    COMMAND searchcorespi_plugin_test_app
    ENVIRONMENT "LD_LIBRARY_PATH=."
    DEPENDS searchcorespi_tplugin searchcorespi_illegal-plugin
)
vespa_add_executable(searchcorespi_factoryregistry_test_app TEST
    SOURCES
    factoryregistry_test.cpp
    DEPENDS
    searchcorespi
)
vespa_add_test(NAME searchcorespi_factoryregistry_test_app COMMAND searchcorespi_factoryregistry_test_app)
