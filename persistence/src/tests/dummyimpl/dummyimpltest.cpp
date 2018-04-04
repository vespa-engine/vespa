// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/persistence/conformancetest/conformancetest.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <vespa/document/repo/documenttyperepo.h>

namespace storage {
namespace spi {

struct DummyImplTest : public ConformanceTest {
    struct Factory : public PersistenceFactory {
        using Repo = document::DocumentTypeRepo;

        PersistenceProvider::UP
        getPersistenceImplementation(const std::shared_ptr<const Repo>& repo, const Repo::DocumenttypesConfig&) override {
            return PersistenceProvider::UP(new dummy::DummyPersistence(repo, 4));
        }

        bool supportsActiveState() const override { return true; }
    };

    DummyImplTest()
        : ConformanceTest(PersistenceFactory::UP(new Factory)) {}

    CPPUNIT_TEST_SUITE(DummyImplTest);
    DEFINE_CONFORMANCE_TESTS();
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(DummyImplTest);

} // spi
} // storage
