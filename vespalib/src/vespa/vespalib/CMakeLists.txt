# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(vespalib
    SOURCES
    $<TARGET_OBJECTS:vespalib_vespalib_btree>
    $<TARGET_OBJECTS:vespalib_vespalib_component>
    $<TARGET_OBJECTS:vespalib_vespalib_crypto>
    $<TARGET_OBJECTS:vespalib_vespalib_data>
    $<TARGET_OBJECTS:vespalib_vespalib_data_slime>
    $<TARGET_OBJECTS:vespalib_vespalib_datastore>
    $<TARGET_OBJECTS:vespalib_vespalib_geo>
    $<TARGET_OBJECTS:vespalib_vespalib_hwaccelrated>
    $<TARGET_OBJECTS:vespalib_vespalib_io>
    $<TARGET_OBJECTS:vespalib_vespalib_locale>
    $<TARGET_OBJECTS:vespalib_vespalib_net>
    $<TARGET_OBJECTS:vespalib_vespalib_net_tls>
    $<TARGET_OBJECTS:vespalib_vespalib_net_tls_impl>
    $<TARGET_OBJECTS:vespalib_vespalib_objects>
    $<TARGET_OBJECTS:vespalib_vespalib_portal>
    $<TARGET_OBJECTS:vespalib_vespalib_stllike>
    $<TARGET_OBJECTS:vespalib_vespalib_test>
    $<TARGET_OBJECTS:vespalib_vespalib_testkit>
    $<TARGET_OBJECTS:vespalib_vespalib_text>
    $<TARGET_OBJECTS:vespalib_vespalib_time>
    $<TARGET_OBJECTS:vespalib_vespalib_trace>
    $<TARGET_OBJECTS:vespalib_vespalib_util>
    $<TARGET_OBJECTS:vespalib_vespalib_websocket>
    INSTALL lib64
    DEPENDS
    ${VESPA_GCC_LIB}
)

vespa_add_target_package_dependency(vespalib OpenSSL)
