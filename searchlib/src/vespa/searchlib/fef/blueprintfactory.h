// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "iblueprintregistry.h"
#include <vespa/vespalib/stllike/string.h>
#include <map>

namespace search::fef {

class Blueprint;
class IIndexEnvironment;
class IDumpFeatureVisitor;

/**
 * This class implements the blueprint repository interface and acts
 * as a blueprint factory for the framework itself.
 **/
class BlueprintFactory : public IBlueprintRegistry
{
private:
    using BlueprintSP = std::shared_ptr<Blueprint>;
    using BlueprintMap = std::map<vespalib::string, BlueprintSP>;

    BlueprintMap _blueprintMap;

public:
    BlueprintFactory(const BlueprintFactory &) = delete;
    BlueprintFactory &operator=(const BlueprintFactory &) = delete;
    BlueprintFactory();

    void addPrototype(BlueprintSP proto) override;

    /**
     * This method will visit features to be dumped by forwarding the
     * visiting request to each of the prototypes registered in this
     * factory.
     *
     * @param indexEnv the index environment
     * @param visitor the object visiting dump features
     **/
    void visitDumpFeatures(const IIndexEnvironment &indexEnv,
                           IDumpFeatureVisitor &visitor) const;

    /**
     * Create a new blueprint instance by using the appropriate
     * prototype contained in this factory. The name given is the
     * feature executor base name (the same one used in the @ref
     * addPrototype method)
     *
     * @return fresh and clean blueprint of the appropriate class
     * @param name feature executor base name
     **/
    BlueprintSP createBlueprint(const vespalib::string &name) const;
};

}
