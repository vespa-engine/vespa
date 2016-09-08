// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include "tensor_conformance.h"
#include <vespa/vespalib/eval/simple_tensor_engine.h>

namespace vespalib {
namespace eval {
namespace test {
namespace {

void dummy_test(const TensorEngine &engine) {
    EXPECT_TRUE(&engine == &SimpleTensorEngine::ref());
}

} // namespace vespalib::eval::test::<unnamed>

void
TensorConformance::run_all_tests() const
{
    TEST_DO(dummy_test(_engine));
}

} // namespace vespalib::eval::test
} // namespace vespalib::eval
} // namespace vespalib
