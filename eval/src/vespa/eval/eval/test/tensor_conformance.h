// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/engine_or_factory.h>
#include <vespa/vespalib/stllike/string.h>

namespace vespalib {
namespace eval {
namespace test {

/**
 * A collection of tensor-related tests that can be run for various
 * implementations of the TensorEngine interface.
 **/
struct TensorConformance {
    static void run_tests(const vespalib::string &module_path, EngineOrFactory engine);
};

} // namespace vespalib::eval::test
} // namespace vespalib::eval
} // namespace vespalib
