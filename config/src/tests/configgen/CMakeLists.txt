# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(config_configgen_test_app TEST
    SOURCES
    configgen.cpp
    DEPENDS
    config_cloudconfig
)
vespa_add_test(NAME config_configgen_test_app COMMAND config_configgen_test_app)
vespa_generate_config(config_configgen_test_app ../../test/resources/configdefinitions/motd.def)
vespa_add_executable(config_vector_inserter_test_app TEST
    SOURCES
    vector_inserter.cpp
    DEPENDS
    config_cloudconfig
)
vespa_add_test(NAME config_vector_inserter_test_app COMMAND config_vector_inserter_test_app)
vespa_add_executable(config_map_inserter_test_app TEST
    SOURCES
    map_inserter.cpp
    DEPENDS
    config_cloudconfig
)
vespa_add_test(NAME config_map_inserter_test_app COMMAND config_map_inserter_test_app)
vespa_add_executable(config_value_converter_test_app TEST
    SOURCES
    value_converter.cpp
    DEPENDS
    config_cloudconfig
)
vespa_add_test(NAME config_value_converter_test_app COMMAND config_value_converter_test_app)
