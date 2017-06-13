// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/persistence/conformancetest/conformancetest.h>
#include <vespa/persistence/proxy/providerstub.h>
#include <vespa/persistence/proxy/providerproxy.h>

namespace storage {
namespace spi {

/**
 * Generic wrapper for persistence conformance test factories. This
 * wrapper will take any other factory and expose a factory interface
 * that will create persistence instances that communicate with
 * persistence instances created by the wrapped factory using the RPC
 * persistence Proxy.
 **/
struct ProxyFactory : ConformanceTest::PersistenceFactory
{
    using Provider =  storage::spi::PersistenceProvider;
    using Client =    storage::spi::ProviderProxy;
    using Repo =      document::DocumentTypeRepo;

    ProxyFactory() {}

    Provider::UP
    getPersistenceImplementation(const Repo::SP &repo, const Repo::DocumenttypesConfig &) override {
        return Provider::UP(new Client("tcp/localhost:3456", *repo));
    }

    bool supportsActiveState() const override {
        return false;
    }
};
}  // namespace spi
}  // namespace storage
