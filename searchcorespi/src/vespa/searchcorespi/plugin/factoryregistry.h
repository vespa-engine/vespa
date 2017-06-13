// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcorespi/plugin/iindexmanagerfactory.h>

namespace searchcorespi {

/**
 */
class FactoryRegistry
{
public:
    FactoryRegistry();
    ~FactoryRegistry();
    /**
     * This will register the plugged in factory under its official
     * name. The plugin should call this method when it is loaded.  E.g.
     * by using either '__attribute__((constructor))' or using a
     * global static object that will use its constructor to register
     * the factory.
     * @param uniqueName This is a name that is unique over all IndexManager factories.
     * @param factory The factory instance for producing IndexManagers.
     * @throws vespalib::IllegalArgument if factory is already registered.
     */
    void add(const vespalib::stringref & uniqueName, const IIndexManagerFactory::SP & factory);
    /**
     * Will unregister a factory. Should be called when a sharedlibrary is being unloaded.
     * @param uniqueName Unique name of factory to remove from registry.
     * @throws vespalib::IllegalArgument if factory is already registered.
     */
    void remove(const vespalib::stringref & uniqueName);
    /**
     * This method will fetch a factory given its unique name.
     * @param name The name of the factory to return.
     * @return The factory.
     */
    const IIndexManagerFactory::SP & get(const vespalib::stringref & uniqueName) const;
    /**
     * Returns true if a factory with the given name has been registered.
     */
    bool isRegistered(const vespalib::stringref & uniqueName) const;

private:
    typedef std::map<vespalib::string, IIndexManagerFactory::SP> Registry;
    Registry       _registry;
    vespalib::Lock _lock;
};

} // namespace searchcorespi

