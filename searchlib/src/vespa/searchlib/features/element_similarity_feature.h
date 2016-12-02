// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>
#include <vector>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/vespalib/util/priority_queue.h>

namespace search {
namespace features {

//-----------------------------------------------------------------------------

class ElementSimilarityBlueprint : public search::fef::Blueprint
{
private:
    struct OutputContext;
    typedef std::unique_ptr<OutputContext> OutputContext_UP;

    uint32_t                      _field_id;
    std::vector<OutputContext_UP> _outputs;

public:
    ElementSimilarityBlueprint();
    virtual ~ElementSimilarityBlueprint();
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment &env,
                                   search::fef::IDumpFeatureVisitor &visitor) const;
    virtual search::fef::Blueprint::UP createInstance() const {
        return Blueprint::UP(new ElementSimilarityBlueprint());
    }
    virtual search::fef::ParameterDescriptions getDescriptions() const {
        return search::fef::ParameterDescriptions().desc().indexField(search::fef::ParameterCollection::ANY);
    }
    virtual bool setup(const search::fef::IIndexEnvironment &env,
                       const search::fef::ParameterList &params);
    virtual search::fef::FeatureExecutor::LP createExecutor(const search::fef::IQueryEnvironment & env) const override;
};

//-----------------------------------------------------------------------------

} // namespace features
} // namespace search

