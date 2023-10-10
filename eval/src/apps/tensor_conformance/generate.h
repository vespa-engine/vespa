// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_spec.h>
#include <map>
#include <set>

struct TestBuilder {
    bool full;
    TestBuilder(bool full_in) : full(full_in) {}
    using TensorSpec = vespalib::eval::TensorSpec;
    virtual void add(const vespalib::string &expression,
                     const std::map<vespalib::string,TensorSpec> &inputs,
                     const std::set<vespalib::string> &ignore) = 0;
    void add(const vespalib::string &expression,
             const std::map<vespalib::string,TensorSpec> &inputs)
    {
        add(expression, inputs, {});
    }
    virtual ~TestBuilder() {}
};

struct Generator {
    static void generate(TestBuilder &out);
};
