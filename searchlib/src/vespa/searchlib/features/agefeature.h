// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <vector>
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchlib/fef/blueprint.h>
#include <vespa/searchlib/fef/featureexecutor.h>

namespace search {
namespace features {

/**
 * Implements the executor for the documentage feature outputting the
 * difference between document time (stored in an attribute) and current
 * system time
 **/
class AgeExecutor : public search::fef::FeatureExecutor {
private:
    const search::attribute::IAttributeVector *_attribute;
    search::attribute::IntegerContent _buf;

public:
    /**
     * Constructs a new executor.
     **/
    AgeExecutor(const search::attribute::IAttributeVector *attribute);
    virtual void execute(search::fef::MatchData & data);
};

/**
 * Implements the blueprint for 'documentage' feature. It uses the 'now' feature
 * to get current time and reads document time from a specified attribute
 */
class AgeBlueprint : public search::fef::Blueprint {
private:
    vespalib::string _attribute;

public:
    AgeBlueprint() : search::fef::Blueprint("age") { }

    // Inherit doc from Blueprint.
    virtual void visitDumpFeatures(const search::fef::IIndexEnvironment &env,
                                   search::fef::IDumpFeatureVisitor &) const;

    // Inherit doc from Blueprint.
    virtual search::fef::Blueprint::UP createInstance() const;

    // Inherit doc from Blueprint.
    virtual search::fef::FeatureExecutor &createExecutor(const search::fef::IQueryEnvironment &env, vespalib::Stash &stash) const override;

    // Inherit doc from Blueprint.
    virtual search::fef::ParameterDescriptions getDescriptions() const {
        return search::fef::ParameterDescriptions().desc().attribute(search::fef::ParameterCollection::ANY);
    }

    // Inherit doc from Blueprint.
    virtual bool setup(const search::fef::IIndexEnvironment & env,
                       const search::fef::ParameterList & params);
};

}
}

