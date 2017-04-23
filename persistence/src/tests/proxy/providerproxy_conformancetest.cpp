// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/persistence/conformancetest/conformancetest.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <vespa/persistence/proxy/providerproxy.h>
#include <vespa/persistence/proxy/providerstub.h>
#include "proxy_factory_wrapper.h"

using namespace storage::spi;
typedef document::DocumentTypeRepo Repo;
typedef ConformanceTest::PersistenceFactory Factory;

namespace {

struct DummyFactory : Factory {
    PersistenceProvider::UP getPersistenceImplementation(const Repo::SP& repo,
                                                         const Repo::DocumenttypesConfig &) override {
        return PersistenceProvider::UP(new dummy::DummyPersistence(repo, 4));
    }

    bool supportsActiveState() const override {
        return true;
    }
};

struct ConformanceFixture : public ConformanceTest {
    ConformanceFixture(Factory::UP f) : ConformanceTest(std::move(f)) { setUp(); }
    ~ConformanceFixture() { tearDown(); }
};

Factory::UP dummyViaProxy(size_t n) {
    if (n == 0) {
        return Factory::UP(new DummyFactory());
    }
    return Factory::UP(new ProxyFactoryWrapper(dummyViaProxy(n - 1)));
}

#define CONVERT_TEST(testFunction, makeFactory)                                             \
namespace ns_ ## testFunction {                                                             \
TEST_F(TEST_STR(testFunction) " " TEST_STR(makeFactory), ConformanceFixture(makeFactory)) { \
    f.testFunction();                                                                       \
}                                                                                           \
} // namespace testFunction

#undef CPPUNIT_TEST
#define CPPUNIT_TEST(testFunction) CONVERT_TEST(testFunction, MAKE_FACTORY)

#define MAKE_FACTORY dummyViaProxy(1)
DEFINE_CONFORMANCE_TESTS();

#undef MAKE_FACTORY
#define MAKE_FACTORY dummyViaProxy(7)
DEFINE_CONFORMANCE_TESTS();

}  // namespace

TEST_MAIN() {
    TEST_RUN_ALL();
}
