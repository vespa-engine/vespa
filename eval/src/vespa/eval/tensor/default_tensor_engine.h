// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/tensor_engine.h>

namespace vespalib {
namespace tensor {

/**
 * This is a tensor engine implementation wrapping the default tensor
 * implementations (dense/sparse).
 **/
class DefaultTensorEngine : public eval::TensorEngine
{
private:
    DefaultTensorEngine() {}
    static const DefaultTensorEngine _engine;
public:
    static const TensorEngine &ref() { return _engine; };

    TensorSpec to_spec(const Value &value) const override;
    Value::UP from_spec(const TensorSpec &spec) const override;

    void encode(const Value &value, nbostream &output) const override;
    Value::UP decode(nbostream &input) const override;

    const TensorFunction &optimize(const TensorFunction &expr, Stash &stash) const override;

    const Value &map(const Value &a, map_fun_t function, Stash &stash) const override;
    const Value &join(const Value &a, const Value &b, join_fun_t function, Stash &stash) const override;
    const Value &reduce(const Value &a, Aggr aggr, const std::vector<vespalib::string> &dimensions, Stash &stash) const override;
    const Value &concat(const Value &a, const Value &b, const vespalib::string &dimension, Stash &stash) const override;
    const Value &rename(const Value &a, const std::vector<vespalib::string> &from, const std::vector<vespalib::string> &to, Stash &stash) const override;
};

} // namespace vespalib::tensor
} // namespace vespalib
