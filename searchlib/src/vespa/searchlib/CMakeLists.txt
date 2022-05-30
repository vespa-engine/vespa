# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
find_package(ICU 60.0 REQUIRED COMPONENTS uc i18n)
vespa_add_library(searchlib
    SOURCES
    $<TARGET_OBJECTS:searchlib_aggregation>
    $<TARGET_OBJECTS:searchlib_attribute>
    $<TARGET_OBJECTS:searchlib_bitcompression>
    $<TARGET_OBJECTS:searchlib_common>
    $<TARGET_OBJECTS:searchlib_diskindex>
    $<TARGET_OBJECTS:searchlib_docstore>
    $<TARGET_OBJECTS:searchlib_engine>
    $<TARGET_OBJECTS:searchlib_expression>
    $<TARGET_OBJECTS:searchlib_features>
    $<TARGET_OBJECTS:searchlib_features_fieldmatch>
    $<TARGET_OBJECTS:searchlib_features_rankingexpression>
    $<TARGET_OBJECTS:searchlib_fef>
    $<TARGET_OBJECTS:searchlib_fef_test>
    $<TARGET_OBJECTS:searchlib_fef_test_plugin>
    $<TARGET_OBJECTS:searchlib_grouping>
    $<TARGET_OBJECTS:searchlib_memoryindex>
    $<TARGET_OBJECTS:searchlib_parsequery>
    $<TARGET_OBJECTS:searchlib_predicate>
    $<TARGET_OBJECTS:searchlib_query>
    $<TARGET_OBJECTS:searchlib_query_streaming>
    $<TARGET_OBJECTS:searchlib_query_tree>
    $<TARGET_OBJECTS:searchlib_queryeval>
    $<TARGET_OBJECTS:searchlib_queryeval_wand>
    $<TARGET_OBJECTS:searchlib_sconfig>
    $<TARGET_OBJECTS:searchlib_searchlib_index>
    $<TARGET_OBJECTS:searchlib_tensor>
    $<TARGET_OBJECTS:searchlib_transactionlog>
    $<TARGET_OBJECTS:searchlib_uca>
    $<TARGET_OBJECTS:searchlib_util>
    $<TARGET_OBJECTS:searchcommon_searchcommon_common>
    $<TARGET_OBJECTS:searchcommon_searchcommon_attribute>

    INSTALL lib64
    DEPENDS
    vespalib
    ICU::i18n
    ICU::uc
    ${VESPA_ATOMIC_LIB}
)

vespa_add_target_package_dependency(searchlib Protobuf)
