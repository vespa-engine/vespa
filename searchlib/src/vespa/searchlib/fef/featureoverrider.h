// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    FeatureOverrider(const FeatureOverrider &);
    FeatureOverrider &operator=(const FeatureOverrider &);

    FeatureExecutor &   _executor;
    uint32_t            _outputIdx;
    FeatureHandle       _handle;
    feature_t           _value;

    virtual void handle_bind_match_data(MatchData &md) override;
public:
    /**
     * Create a feature overrider that will override the given output
     * with the given feature value.
     *
     * @param executor the feature executor for which we should override an output
     * @param outputIdx which output to override
     * @param value what value to override with
     **/
    FeatureOverrider(FeatureExecutor &executor, uint32_t outputIdx, feature_t value);
    void inputs_done() override;
    void outputs_done() override;
    bool isPure() override;
    void execute(MatchData &data) override;
};

} // namespace fef
} // namespace search

