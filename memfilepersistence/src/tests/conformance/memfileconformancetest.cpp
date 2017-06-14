// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/memfilepersistence/spi/memfilepersistence.h>
#include <vespa/persistence/conformancetest/conformancetest.h>

#include <vespa/log/log.h>
LOG_SETUP(".test.conformance");

using namespace storage::spi;

namespace storage {
namespace memfile {

    /*
struct MemFileConformanceTest : public ConformanceTest {
    struct Factory : public PersistenceFactory {

        PersistenceSPI::UP getPersistenceImplementation() {
            return PersistenceSPI::UP(new MemFilePersistence);
        }
    };

    MemFileConformanceTest()
        : ConformanceTest(PersistenceFactory::UP(new Factory)) {}

    CPPUNIT_TEST_SUITE(MemFileConformanceTest);
    DEFINE_CONFORMANCE_TESTS();
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(MemFileConformanceTest);
*/

} // memfile
} // storage
