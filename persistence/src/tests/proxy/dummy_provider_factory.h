// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/persistence/proxy/providerstub.h>
#include <memory>

namespace storage {
namespace spi {

/**
 * A simple rpc server persistence provider factory that will only
 * work once, by returning a precreated persistence provider instance.
 **/
struct DummyProviderFactory : ProviderStub::PersistenceProviderFactory
{
    typedef std::unique_ptr<DummyProviderFactory> UP;
    typedef storage::spi::PersistenceProvider Provider;

    mutable std::unique_ptr<Provider> provider;

    DummyProviderFactory(std::unique_ptr<Provider> p) : provider(std::move(p)) {}

    std::unique_ptr<Provider> create() const override {
        ASSERT_TRUE(provider.get() != 0);
        std::unique_ptr<Provider> ret = std::move(provider);
        ASSERT_TRUE(provider.get() == 0);
        return ret;
    }
};

}  // namespace spi
}  // namespace storage

