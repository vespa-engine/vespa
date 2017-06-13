// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcorespi/plugin/factoryregistry.h>
#include <vespa/searchcorespi/plugin/iindexmanagerfactory.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/exceptions.h>

using vespalib::string;
using namespace searchcorespi;

namespace {

struct MyFactory : IIndexManagerFactory {

    virtual IIndexManager::UP createIndexManager(const IndexManagerConfig &,
                                                 const index::IndexMaintainerConfig &,
                                                 const index::IndexMaintainerContext &) override {
        return IIndexManager::UP();
    }
    virtual config::ConfigKeySet getConfigKeys(
            const string &,
            const search::index::Schema &,
            const config::ConfigInstance &) override {
        return config::ConfigKeySet();
    }
};

const string name = "factory";

TEST("require that factories can be added and removed") {
    FactoryRegistry registry;
    EXPECT_FALSE(registry.isRegistered(name));
    registry.add(name, IIndexManagerFactory::SP(new MyFactory));
    EXPECT_TRUE(registry.get(name).get());
    EXPECT_TRUE(registry.isRegistered(name));
    registry.remove(name);
    EXPECT_EXCEPTION(registry.get(name), vespalib::IllegalArgumentException,
                     "No factory is registered with the name");
}

TEST("require that two factories with the same name cannot be added") {
    FactoryRegistry registry;
    registry.add(name, IIndexManagerFactory::SP(new MyFactory));
    EXPECT_EXCEPTION(
            registry.add(name, IIndexManagerFactory::SP(new MyFactory)),
            vespalib::IllegalArgumentException,
            "A factory is already registered with the same name");
}

TEST("require that a non-existent factory cannot be removed") {
    FactoryRegistry registry;
    EXPECT_EXCEPTION(registry.remove(name), vespalib::IllegalArgumentException,
                     "No factory is registered with the name");
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
