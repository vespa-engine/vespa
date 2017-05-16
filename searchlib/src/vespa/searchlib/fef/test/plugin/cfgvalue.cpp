// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".fef.cfgvalue");

#include <vespa/searchlib/fef/properties.h>
#include <sstream>
#include "cfgvalue.h"

namespace search {
namespace fef {
namespace test {

CfgValueBlueprint::CfgValueBlueprint() :
    Blueprint("test_cfgvalue"),
    _values()
{
}

CfgValueBlueprint::~CfgValueBlueprint()
{
}

void
CfgValueBlueprint::visitDumpFeatures(const IIndexEnvironment &indexEnv, IDumpFeatureVisitor &visitor) const
{
    Property p = indexEnv.getProperties().lookup(getBaseName(), "dump");
    for (uint32_t i = 0; i < p.size(); ++i) {
        visitor.visitDumpFeature(p.getAt(i));
    }
}

bool
CfgValueBlueprint::setup(const IIndexEnvironment &indexEnv, const StringVector &params)
{
    (void) params;
    Property p = indexEnv.getProperties().lookup(getName(), "value");
    for (uint32_t i = 0; i < p.size(); ++i) {
        std::istringstream iss(p.getAt(i));
        feature_t value;
        iss >> std::dec >> value;
        _values.push_back(value);

        if (iss.fail()) {
            return false;
        }

        std::ostringstream name;
        name << i;
        std::ostringstream desc;
        desc << "value " << i;
        describeOutput(name.str(), desc.str());
        // we have no inputs
    }
    return true;
}

FeatureExecutor &
CfgValueBlueprint::createExecutor(const IQueryEnvironment & queryEnv, vespalib::Stash &stash) const
{
    (void) queryEnv;
    return stash.create<search::features::ValueExecutor>(_values);
}

} // namespace test
} // namespace fef
} // namespace search
