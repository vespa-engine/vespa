// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/persistence/conformancetest/conformancetest.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <vespa/document/repo/documenttyperepo.h>

#include <vespa/log/log.h>
LOG_SETUP("persistence_dummyimpl_conformance_test");

namespace storage::spi {

namespace {

struct DummyPersistenceFactory : public ConformanceTest::PersistenceFactory {
    using Repo = document::DocumentTypeRepo;

    PersistenceProvider::UP
    getPersistenceImplementation(const std::shared_ptr<const Repo>& repo, const Repo::DocumenttypesConfig&) override {
        return PersistenceProvider::UP(new dummy::DummyPersistence(repo, 4));
    }

    bool supportsActiveState() const override { return true; }
};

std::unique_ptr<ConformanceTest::PersistenceFactory>
makeDummyPersistenceFactory(const std::string &)
{
    return std::make_unique<DummyPersistenceFactory>();
}

}

}

int
main(int argc, char* argv[])
{
    ::testing::InitGoogleTest(&argc, argv);
    storage::spi::ConformanceTest::_factoryFactory = &storage::spi::makeDummyPersistenceFactory;
    return RUN_ALL_TESTS();
}
