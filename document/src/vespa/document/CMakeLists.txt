# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(document
    SOURCES
    $<TARGET_OBJECTS:document_util>
    $<TARGET_OBJECTS:document_base>
    $<TARGET_OBJECTS:document_bucket>
    $<TARGET_OBJECTS:document_datatypes>
    $<TARGET_OBJECTS:document_fieldvalues>
    $<TARGET_OBJECTS:document_select>
    $<TARGET_OBJECTS:document_updates>
    $<TARGET_OBJECTS:document_fieldset>
    $<TARGET_OBJECTS:document_documentconfig>
    $<TARGET_OBJECTS:document_annotation>
    $<TARGET_OBJECTS:document_repo>
    $<TARGET_OBJECTS:document_serialization>
    $<TARGET_OBJECTS:document_predicate>
    $<TARGET_OBJECTS:document_test>
    INSTALL lib64
    DEPENDS
)
