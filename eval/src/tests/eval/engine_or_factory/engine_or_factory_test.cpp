// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/engine_or_factory.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib::eval;
using namespace vespalib::tensor;

TEST(EngineOrFactoryTest, default_is_default_tensor_engine) {
    EXPECT_EQ(EngineOrFactory::get().to_string(), "DefaultTensorEngine");
}

TEST(EngineOrFactoryTest, set_with_same_value_is_allowed) {
    EngineOrFactory::set(DefaultTensorEngine::ref());
}

TEST(EngineOrFactoryTest, set_with_another_value_is_not_allowed) {
    EXPECT_THROW(EngineOrFactory::set(FastValueBuilderFactory::get()), vespalib::IllegalStateException);
}

GTEST_MAIN_RUN_ALL_TESTS()
