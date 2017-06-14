// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchcorespi/index/iindexmanager.h>
#include <vespa/searchcorespi/index/indexmaintainerconfig.h>
#include <vespa/searchcorespi/index/indexmaintainercontext.h>
#include <vespa/searchcorespi/index/indexmanagerconfig.h>
#include <vespa/config/configgen/configinstance.h>
#include <vespa/config/retriever/configsnapshot.h>
#include <vespa/config/retriever/configkeyset.h>

namespace searchcorespi {

/**
 * Interface for an index manager factory. Every provider of an index manager is supposed to provide a
 * factory for producing them. It is given the basedir, the schema and a collection of configs.
 * The factory implementation must pick the config it needs and return an IIndexManager instance.
 * The factory is registered by using the registerFactory() method.
 */
class IIndexManagerFactory
{
public:
    typedef std::shared_ptr<IIndexManagerFactory> SP;
    typedef std::unique_ptr<IIndexManagerFactory> UP;

    virtual ~IIndexManagerFactory() {}

    /**
     * This method will be called by a document db when it needs to create an index manager that
     * uses an index maintainer (with source selector) in its implementation.
     * The factory implementation must use RTTI to figure out what configs are what.
     * It should receive all configs it needs, but wise to do sanity checking.
     *
     * @param managerConfig The config that will be used to construct an index manager.
     *                      Note that if the factory used a different config id when populating the
     *                      ConfigKeySet compared to the one in this config instance, it must
     *                      also override the config id when fetching from the config snapshot.
     *                      The root config received in the @ref getConfigKeys() call will also be
     *                      part of the config snapshot in this config instance.
     * @param maintainerConfig The config needed to construct an index maintainer.
     * @param maintainerContext The context object used by an index maintainer during its lifetime.
     * @return The index manager created or NULL if not, fx if configs are not as expected.
     */
    virtual IIndexManager::UP createIndexManager(const IndexManagerConfig &managerConfig,
                                                 const index::IndexMaintainerConfig &maintainerConfig,
                                                 const index::IndexMaintainerContext &maintainerContext) = 0;

    /**
     * The factory must return the set of config keys that it will require the config from.
     * This will facilitate that the searchcore can fetch all configs needed in a pluggable way.
     *
     * @param configId The config id to use when generating the config keys.
     * @param schema This is the initial index schema to be used.
     * @param rootConfig This is an config instance that is the root config for the factory.
     *                   Based on this config it must be able to tell if it needs any other config,
     *                   and in that case provide the config keys.
     * @return The set containing keys for all configs required.
     */
    virtual config::ConfigKeySet getConfigKeys(const vespalib::string &configId,
                                               const search::index::Schema &schema,
                                               const config::ConfigInstance &rootConfig) = 0;
};

} // namespace searchcorespi

extern "C" {
/**
 * This is a method that each shared library must have in order provide a factory.
 * This will be called by the one loading the library.
 * @return The created factory that the caller will take ownership of.
 */
searchcorespi::IIndexManagerFactory * createIndexManagerFactory();

}


