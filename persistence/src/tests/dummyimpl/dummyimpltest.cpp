// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/persistence/conformancetest/conformancetest.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>

LOG_SETUP(".test.dummyimpl");

namespace storage {
namespace spi {

struct DummyImplTest : public ConformanceTest {
    struct Factory : public PersistenceFactory {

        PersistenceProvider::UP
        getPersistenceImplementation(const document::DocumentTypeRepo::SP& repo,
                                     const document::DocumenttypesConfig&) {
            return PersistenceProvider::UP(new dummy::DummyPersistence(repo, 4));
        }

        bool
        supportsActiveState() const
        {
            return true;
        }
        bool
        supportsRevert() const
        {
            return true;
        }
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
