// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>
#include <vespa/eval/eval/value_type.h>

namespace search {
namespace features {


/**
 * Implements the blueprint for the attribute executor.
 *
 * An executor of this outputs number(s) if used with regular attributes
 * or a tensor value if used with tensor attributes.
 */
class AttributeBlueprint : public search::fef::Blueprint {
private:
    vespalib::string _attrName; // the name of the attribute vector
    vespalib::string _extra;    // the index or key
    vespalib::eval::ValueType _tensorType;

public:
    /**
     * Constructs a blueprint.
     */
    AttributeBlueprint();

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment & env,
                                   search::fef::IDumpFeatureVisitor & visitor) const;

    // Inherit doc from Blueprint.
    virtual search::fef::Blueprint::UP createInstance() const;

    // Inherit doc from Blueprint.
    virtual search::fef::FeatureExecutor &createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;

    // Inherit doc from Blueprint.
    virtual search::fef::ParameterDescriptions getDescriptions() const {
        return search::fef::ParameterDescriptions().
            desc().attribute(search::fef::ParameterCollection::ANY).
            desc().attribute(search::fef::ParameterCollection::ANY).string();
    }

    // Inherit doc from Blueprint.
    virtual bool setup(const search::fef::IIndexEnvironment & env,
                       const search::fef::ParameterList & params);
};


} // namespace features
} // namespace search

