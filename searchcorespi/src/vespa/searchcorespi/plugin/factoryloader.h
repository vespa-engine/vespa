// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcorespi/plugin/iindexmanagerfactory.h>
#include <vespa/vespalib/util/librarypool.h>

namespace searchcorespi {

class FactoryLoader
{
public:
    FactoryLoader();
    ~FactoryLoader();
    /**
     * Will load the library containing the factory. It will then locate the 'createIndexManagerFactory'
     * symbol and run it to create the factory.
     * @param the name of the library. Like 'vesparise'.
     * @return the factory that is created.
     */
    IIndexManagerFactory::UP create(vespalib::stringref factory);
private:
    vespalib::LibraryPool _libraries;
};

} // namespace searchcorespi

