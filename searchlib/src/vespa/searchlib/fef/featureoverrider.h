// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "featureexecutor.h"

namespace search::fef {

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

    FeatureExecutor &   _executor;
    uint32_t            _outputIdx;
    feature_t           _number;
    Value::UP           _object;

    void handle_bind_match_data(const MatchData &md) override;
    void handle_bind_inputs(std::span<const LazyValue> inputs) override;
    void handle_bind_outputs(std::span<NumberOrObject> outputs) override;

public:
    FeatureOverrider(const FeatureOverrider &) = delete;
    FeatureOverrider &operator=(const FeatureOverrider &) = delete;
    FeatureOverrider(FeatureExecutor &executor, uint32_t outputIdx, feature_t number, Value::UP object);
    bool isPure() override;
    void execute(uint32_t docId) override;
};

}
