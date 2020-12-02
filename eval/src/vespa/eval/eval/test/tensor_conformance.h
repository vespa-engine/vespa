// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/stllike/string.h>

namespace vespalib::eval::test {

/**
 * A collection of tensor-related tests that can be run for various
 * implementations.
 **/
struct TensorConformance {
    static void run_tests(const vespalib::string &module_path, const ValueBuilderFactory &factory);
};

} // namespace
