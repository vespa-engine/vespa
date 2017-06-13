// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "proxyfactory.h"
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/persistence/conformancetest/conformancetest.h>
#include <vespa/persistence/proxy/providerproxy.h>
#include <vespa/persistence/proxy/providerstub.h>

using namespace storage::spi;
typedef document::DocumentTypeRepo Repo;
typedef ConformanceTest::PersistenceFactory Factory;

namespace {

struct ConformanceFixture : public ConformanceTest {
    ConformanceFixture(Factory::UP f) : ConformanceTest(std::move(f)) { setUp(); }
    ~ConformanceFixture() { tearDown(); }
};

Factory::UP getFactory() {
    return Factory::UP(new ProxyFactory());
}

#define CONVERT_TEST(testFunction, makeFactory)                                             \
namespace ns_ ## testFunction {                                                             \
TEST_F(TEST_STR(testFunction) " " TEST_STR(makeFactory), ConformanceFixture(makeFactory)) { \
    f.testFunction();                                                                       \
}                                                                                           \
} // namespace testFunction

#undef CPPUNIT_TEST
#define CPPUNIT_TEST(testFunction) CONVERT_TEST(testFunction, MAKE_FACTORY)

#define MAKE_FACTORY getFactory()
DEFINE_CONFORMANCE_TESTS();

}  // namespace

TEST_MAIN() {
    TEST_RUN_ALL();
}
