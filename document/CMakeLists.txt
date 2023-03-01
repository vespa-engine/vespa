# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_define_module(
    DEPENDS
    vespalog
    vespalib
    config_cloudconfig
    vespaeval

    LIBS
    src/vespa/document
    src/vespa/document/annotation
    src/vespa/document/base
    src/vespa/document/bucket
    src/vespa/document/config
    src/vespa/document/datatype
    src/vespa/document/fieldset
    src/vespa/document/fieldvalue
    src/vespa/document/predicate
    src/vespa/document/repo
    src/vespa/document/select
    src/vespa/document/serialization
    src/vespa/document/update
    src/vespa/document/util
    src/vespa/document/test

    TESTS
    src/tests
    src/tests/annotation
    src/tests/base
    src/tests/datatype
    src/tests/document_type_repo_factory
    src/tests/fieldvalue
    src/tests/predicate
    src/tests/repo
    src/tests/select
    src/tests/serialization
    src/tests/struct_anno
    src/tests/tensor_fieldvalue
    src/tests/tensor_fieldvalue/partial_add
    src/tests/tensor_fieldvalue/partial_modify
    src/tests/tensor_fieldvalue/partial_remove
)

install_jar(document.jar)
