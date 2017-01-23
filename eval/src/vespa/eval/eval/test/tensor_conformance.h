// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_engine.h>

namespace vespalib {
namespace eval {
namespace test {

/**
 * A collection of tensor-related tests that can be run for various
 * implementations of the TensorEngine interface.
 **/
struct TensorConformance {
    static void run_tests(const TensorEngine &engine, bool test_mixed_cases);
};

} // namespace vespalib::eval::test
} // namespace vespalib::eval
} // namespace vespalib
