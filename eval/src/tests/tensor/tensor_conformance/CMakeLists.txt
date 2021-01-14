# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_executable(eval_tensor_tensor_conformance_test_app TEST
    SOURCES
    tensor_conformance_test.cpp
    DEPENDS
    vespaeval
)
vespa_add_test(NAME eval_tensor_tensor_conformance_test_app COMMAND eval_tensor_tensor_conformance_test_app COST 150)
