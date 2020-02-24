// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sum.h"
#include <vespa/searchlib/fef/featurenamebuilder.h>
#include <vespa/vespalib/util/stash.h>

namespace search::fef::test {

void
SumExecutor::execute(uint32_t)
{
    feature_t sum = 0.0f;
    for (uint32_t i = 0; i < inputs().size(); ++i) {
        sum += inputs().get_number(i);
    }
    outputs().set_number(0, sum);
}


SumBlueprint::SumBlueprint() :
    Blueprint("mysum")
{
}

void
SumBlueprint::visitDumpFeatures(const IIndexEnvironment & indexEnv, IDumpFeatureVisitor & visitor) const
{
    (void) indexEnv;
#if 1
    (void) visitor;
#else
    // Use the feature name builder to make sure that the naming of features are quoted correctly.
    typedef FeatureNameBuilder FNB;

    // This blueprint dumps 2 ranking features. This is a very tricky feature in that it's dependencies
    // are given by its parameters, so the definition of features implicitly declares this tree. This
    // blueprint can actually produce any number of features, but only the following 2 are ever dumped.

    // The first feature this produces is "sum(value(4),value(16))", quoted correctly by the feature name
    // builder. The feature "value" simply returns the value of its single parameter, so this feature will
    // always produce the output "20".
    visitor.visitDumpFeature(FNB().baseName("sum").parameter("value(4)").parameter("value(16)").buildName());

    // The second feature is "sum(double(value(8)),double(value(32)))", again quoted by the feature name
    // builder. The feature "double" returns twice the value of its single input. This means that this
    // feature will always produce the output "80" (= 8*2 + 32*2).
    std::string d1 = FNB().baseName("double").parameter("value(8)").buildName();
    std::string d2 = FNB().baseName("double").parameter("value(32)").buildName();
    visitor.visitDumpFeature(FNB().baseName("sum").parameter(d1).parameter(d2).buildName());
#endif
}

bool
SumBlueprint::setup(const IIndexEnvironment & indexEnv, const StringVector & params)
{
    (void) indexEnv;

    // This blueprints expects all parameters to be complete feature names, so depend on these.
    for (uint32_t i = 0; i < params.size(); ++i) {
        defineInput(params[i]);
    }

    // Produce only a single output named "out".
    describeOutput("out", "The sum of the values of all parameter features.");
    return true;
}

FeatureExecutor &
SumBlueprint::createExecutor(const IQueryEnvironment &queryEnv, vespalib::Stash &stash) const
{
    (void) queryEnv;
    return stash.create<SumExecutor>();
}

}
