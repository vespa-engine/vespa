// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "blueprintfactory.h"
#include "blueprint.h"

#include <vespa/log/log.h>
LOG_SETUP(".fef.blueprintfactory");

namespace search::fef {

BlueprintFactory::BlueprintFactory()
    : _blueprintMap()
{
}

void
BlueprintFactory::addPrototype(Blueprint::SP proto)
{
    vespalib::string name = proto->getBaseName();
    if (_blueprintMap.find(name) != _blueprintMap.end()) {
        LOG(warning, "Blueprint prototype overwritten: %s", name.c_str());
    }
    _blueprintMap[name] = std::move(proto);
}

void
BlueprintFactory::visitDumpFeatures(const IIndexEnvironment &indexEnv,
                                    IDumpFeatureVisitor &visitor) const
{
    for (const auto & entry : _blueprintMap) {
        entry.second->visitDumpFeatures(indexEnv, visitor);
    }
}

Blueprint::SP
BlueprintFactory::createBlueprint(const vespalib::string &name) const
{
    auto itr = _blueprintMap.find(name);
    if (itr == _blueprintMap.end()) {
        return {};
    }
    return itr->second->createInstance();
}

}
