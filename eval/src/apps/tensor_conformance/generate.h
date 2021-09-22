// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    void add_ignore_java(const vespalib::string &expression,
                         const std::map<vespalib::string,TensorSpec> &inputs)
    {
        add(expression, inputs, {"vespajlib"});
    }
    virtual ~TestBuilder() {}
};

struct Generator {
    static void generate(TestBuilder &out);
};
