// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/vespalib/util/priority_queue.h>

namespace search::features {

//-----------------------------------------------------------------------------

class ElementSimilarityBlueprint : public fef::Blueprint
{
private:
    struct OutputContext;
    using OutputContext_UP = std::unique_ptr<OutputContext>;

    uint32_t                      _field_id;
    std::vector<OutputContext_UP> _outputs;

public:
    ElementSimilarityBlueprint();
    ~ElementSimilarityBlueprint();
    void visitDumpFeatures(const fef::IIndexEnvironment &env,
                           fef::IDumpFeatureVisitor &visitor) const override;
    fef::Blueprint::UP createInstance() const override {
        return Blueprint::UP(new ElementSimilarityBlueprint());
    }
    fef::ParameterDescriptions getDescriptions() const override {
        return fef::ParameterDescriptions().desc().indexField(fef::ParameterCollection::ANY);
    }
    bool setup(const fef::IIndexEnvironment &env, const fef::ParameterList &params) override;
    fef::FeatureExecutor &createExecutor(const fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;
};

}
