// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "blueprint.h"
#include "iblueprintregistry.h"
#include <map>

namespace search::fef {

/**
 * This class implements the blueprint repository interface and acts
 * as a blueprint factory for the framework itself.
 **/
class BlueprintFactory : public IBlueprintRegistry
{
private:
    BlueprintFactory(const BlueprintFactory &);
    BlueprintFactory &operator=(const BlueprintFactory &);

    typedef std::map<vespalib::string, Blueprint::SP> BlueprintMap;

    BlueprintMap _blueprintMap;

public:
    /**
     * Create an empty factory.
     **/
    BlueprintFactory();

    void addPrototype(Blueprint::SP proto) override;

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
    Blueprint::SP createBlueprint(const vespalib::string &name) const;
};

}
