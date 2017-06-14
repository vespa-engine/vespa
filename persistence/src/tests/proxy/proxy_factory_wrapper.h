// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/persistence/conformancetest/conformancetest.h>
#include <vespa/persistence/proxy/providerstub.h>
#include <vespa/persistence/proxy/providerproxy.h>
#include "dummy_provider_factory.h"

namespace storage {
namespace spi {

/**
 * Generic wrapper for persistence conformance test factories. This
 * wrapper will take any other factory and expose a factory interface
 * that will create persistence instances that communicate with
 * persistence instances created by the wrapped factory using the RPC
 * persistence Proxy.
 **/
struct ProxyFactoryWrapper : ConformanceTest::PersistenceFactory
{
    typedef storage::spi::ConformanceTest::PersistenceFactory Factory;
    typedef storage::spi::PersistenceProvider                 Provider;
    typedef storage::spi::ProviderStub                        Server;
    typedef storage::spi::ProviderProxy                       Client;
    typedef document::DocumentTypeRepo                        Repo;

    Factory::UP factory;
    ProxyFactoryWrapper(Factory::UP f) : factory(std::move(f)) {}

    struct Wrapper : Client {
        DummyProviderFactory::UP provider;
        Server::UP server;
        Wrapper(DummyProviderFactory::UP p, Server::UP s, const Repo &repo)
            : Client(vespalib::make_string("tcp/localhost:%u", s->getPort()), repo),
              provider(std::move(p)),
              server(std::move(s))
        {}
    };

    virtual Provider::UP
    getPersistenceImplementation(const Repo::SP &repo,
                                 const Repo::DocumenttypesConfig &typesCfg) override{
        DummyProviderFactory::UP provider(new DummyProviderFactory(factory->getPersistenceImplementation(repo, typesCfg)));
        Server::UP server(new Server(0, 8, *repo, *provider));
        return Provider::UP(new Wrapper(std::move(provider), std::move(server), *repo));
    }

    bool supportsActiveState() const override  {
        return factory->supportsActiveState();
    }
};
}  // namespace spi
}  // namespace storage

