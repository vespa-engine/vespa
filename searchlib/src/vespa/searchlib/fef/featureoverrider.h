// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "featureexecutor.h"

namespace search {
namespace fef {

/**
 * A Feature Overrider is a simple decorator class that wraps a single
 * Feature Executor instance and overrides one of its output
 * features. All method invocations are passed through to the inner
 * feature executor. Each time the execute method is invoked, the
 * appropriate feature value is overwritten.
 **/
class FeatureOverrider : public FeatureExecutor
{
private:
    using Value = vespalib::eval::Value;

    FeatureOverrider(const FeatureOverrider &);
    FeatureOverrider &operator=(const FeatureOverrider &);

    FeatureExecutor &   _executor;
    uint32_t            _outputIdx;
    feature_t           _number;
    Value::UP           _object;

    virtual void handle_bind_match_data(const MatchData &md) override;
    virtual void handle_bind_inputs(vespalib::ConstArrayRef<LazyValue> inputs) override;
    virtual void handle_bind_outputs(vespalib::ArrayRef<NumberOrObject> outputs) override;

public:
    FeatureOverrider(FeatureExecutor &executor, uint32_t outputIdx, feature_t number, Value::UP object);
    bool isPure() override;
    void execute(uint32_t docId) override;
};

} // namespace fef
} // namespace search
