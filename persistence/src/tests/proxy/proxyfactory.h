// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/vstringfmt.h>
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
    typedef storage::spi::PersistenceProvider                 Provider;
    typedef storage::spi::ProviderProxy                       Client;
    typedef document::DocumentTypeRepo                        Repo;

    ProxyFactory() {}

    virtual Provider::UP
    getPersistenceImplementation(const document::DocumentTypeRepo::SP &repo,
                                 const document::DocumenttypesConfig &) {
        return Provider::UP(new Client("tcp/localhost:3456", *repo));
    }

    virtual bool
    supportsActiveState() const
    {
        return false;
    }
};
}  // namespace spi
}  // namespace storage

