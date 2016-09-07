// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/eval/tensor_engine.h>

namespace vespalib {
namespace eval {
namespace test {

/**
 * A collection of tensor-related tests that can be run for various
 * implementations of the TensorEngine interface.
 **/
class TensorConformance
{
private:
    const TensorEngine &_engine;
public:
    TensorConformance(const TensorEngine &engine) : _engine(engine) {}
    void run_all_tests() const;
};

} // namespace vespalib::eval::test
} // namespace vespalib::eval
} // namespace vespalib
