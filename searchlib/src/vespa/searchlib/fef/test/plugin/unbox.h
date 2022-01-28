// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>

namespace search::fef::test {

struct UnboxBlueprint : Blueprint {
    bool _was_object;
    UnboxBlueprint();
    void visitDumpFeatures(const IIndexEnvironment &, IDumpFeatureVisitor &) const override;
    Blueprint::UP createInstance() const override;
    ParameterDescriptions getDescriptions() const  override;
    bool setup(const IIndexEnvironment &, const ParameterList &params) override;
    FeatureExecutor &createExecutor(const IQueryEnvironment &, vespalib::Stash &stash) const override;
};

} // namespace search::fef::test
