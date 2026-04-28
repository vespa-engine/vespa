# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set(VESPA_LIBSTDCXX_COMPAT "")
if ((${VESPA_OS_DISTRO} STREQUAL "almalinux" OR ${VESPA_OS_DISTRO} STREQUAL "rocky" OR ${VESPA_OS_DISTRO} STREQUAL "centos") AND
    ${VESPA_OS_DISTRO_VERSION} VERSION_GREATER_EQUAL 9 AND ${VESPA_OS_DISTRO_VERSION} VERSION_LESS 10)
    set(VESPA_LIBSTDCXX_COMPAT_SOURCE_LIBRARY "/opt/rh/gcc-toolset-15/root/usr/lib/gcc/${CMAKE_SYSTEM_PROCESSOR}-redhat-linux/15/libstdc++.a")
    if (EXISTS "${VESPA_LIBSTDCXX_COMPAT_SOURCE_LIBRARY}")
        # Need to use the version of std::from_chars that comes with gcc-toolset-15 instead ot the version in gcc 11.
        set(VESPA_LIBSTDCXX_COMPAT vespa_libstdcxx_compat)
        set(FROM_CHARS_OBJECT floating_from_chars.o)
        add_library(vespa_libstdcxx_compat_object OBJECT IMPORTED GLOBAL)
        set_property(TARGET vespa_libstdcxx_compat_object PROPERTY IMPORTED_OBJECTS ${CMAKE_CURRENT_BINARY_DIR}/${FROM_CHARS_OBJECT})
        add_library(vespa_libstdcxx_compat STATIC
           $<TARGET_OBJECTS:vespa_libstdcxx_compat_object>
        )
        set_target_properties(vespa_libstdcxx_compat PROPERTIES LINKER_LANGUAGE CXX)
        add_custom_command(
            OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/${FROM_CHARS_OBJECT}
            COMMAND ar xv "${VESPA_LIBSTDCXX_COMPAT_SOURCE_LIBRARY}" ${FROM_CHARS_OBJECT}
            WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}
            COMMENT "Extracting ${FROM_CHARS_OBJECT} from ${VESPA_LIBSTDCXX_COMPAT_SOURCE_LIBRARY}"
        )
        message("-- Adding vespa_libstdcxx_compat library for ${VESPA_OS_DISTRO_COMBINED}/${CMAKE_SYSTEM_PROCESSOR}")
    endif()
endif()
vespa_add_library(vespalib
    SOURCES
    $<TARGET_OBJECTS:vespalib_vespalib_btree>
    $<TARGET_OBJECTS:vespalib_vespalib_component>
    $<TARGET_OBJECTS:vespalib_vespalib_coro>
    $<TARGET_OBJECTS:vespalib_vespalib_crypto>
    $<TARGET_OBJECTS:vespalib_vespalib_data>
    $<TARGET_OBJECTS:vespalib_vespalib_data_slime>
    $<TARGET_OBJECTS:vespalib_vespalib_datastore>
    $<TARGET_OBJECTS:vespalib_vespalib_encoding>
    $<TARGET_OBJECTS:vespalib_vespalib_fuzzy>
    $<TARGET_OBJECTS:vespalib_vespalib_geo>
    $<TARGET_OBJECTS:vespalib_vespalib_io>
    $<TARGET_OBJECTS:vespalib_vespalib_locale>
    $<TARGET_OBJECTS:vespalib_vespalib_metrics>
    $<TARGET_OBJECTS:vespalib_vespalib_net>
    $<TARGET_OBJECTS:vespalib_vespalib_net_http>
    $<TARGET_OBJECTS:vespalib_vespalib_net_tls>
    $<TARGET_OBJECTS:vespalib_vespalib_net_tls_impl>
    $<TARGET_OBJECTS:vespalib_vespalib_objects>
    $<TARGET_OBJECTS:vespalib_vespalib_portal>
    $<TARGET_OBJECTS:vespalib_vespalib_process>
    $<TARGET_OBJECTS:vespalib_vespalib_regex>
    $<TARGET_OBJECTS:vespalib_vespalib_stllike>
    $<TARGET_OBJECTS:vespalib_vespalib_test>
    $<TARGET_OBJECTS:vespalib_vespalib_text>
    $<TARGET_OBJECTS:vespalib_vespalib_time>
    $<TARGET_OBJECTS:vespalib_vespalib_trace>
    $<TARGET_OBJECTS:vespalib_vespalib_util>
    $<TARGET_OBJECTS:vespalib_fastlib_io>
    $<TARGET_OBJECTS:vespalib_fastlib_text>
    $<TARGET_OBJECTS:vespalib_fastos>
    INSTALL lib64
    EXTERNAL_DEPENDS
    ${VESPA_GCC_LIB}
    ${VESPA_LIBSTDCXX_COMPAT}
)

vespa_add_target_package_dependency(vespalib OpenSSL)
vespa_add_target_package_dependency(vespalib RE2)
