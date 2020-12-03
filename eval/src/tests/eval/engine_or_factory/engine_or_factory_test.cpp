// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/engine_or_factory.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib::eval;

TEST(EngineOrFactoryTest, default_is_fast_value_builder_factory) {
    EXPECT_EQ(EngineOrFactory::get().to_string(), "FastValueBuilderFactory");
}

TEST(EngineOrFactoryTest, set_with_same_value_is_allowed) {
    EngineOrFactory::set(FastValueBuilderFactory::get());
}

TEST(EngineOrFactoryTest, set_with_another_value_is_not_allowed) {
    EXPECT_THROW(EngineOrFactory::set(SimpleValueBuilderFactory::get()), vespalib::IllegalStateException);
}

GTEST_MAIN_RUN_ALL_TESTS()
