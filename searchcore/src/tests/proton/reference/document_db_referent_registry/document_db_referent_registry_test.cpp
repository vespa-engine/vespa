// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchcore/proton/reference/document_db_referent_registry.h>
#include <vespa/searchcore/proton/test/mock_document_db_referent.h>
#include <thread>
#include <vespa/log/log.h>
LOG_SETUP("document_db_reference_registry_test");

namespace proton
{

namespace
{


std::mutex lock;
std::shared_ptr<IDocumentDBReferent> getFooResult;

void getFooTask(const IDocumentDBReferentRegistry *registry)
{
    auto result = registry->get("foo");
    std::lock_guard<std::mutex> guard(lock);
    getFooResult = result;
}

std::shared_ptr<IDocumentDBReferent> checkFooResult()
{
    std::lock_guard<std::mutex> guard(lock);
    return getFooResult;
}

}

struct Fixture
{

    DocumentDBReferentRegistry _registry;

    Fixture()
        : _registry()
    {
    }

    test::MockDocumentDBReferent::SP
    add(vespalib::string name) {
        auto referent = std::make_shared<test::MockDocumentDBReferent>();
        _registry.add(name, referent);
        return referent;
    }
};

TEST_F("Test that we can instantiate registry with two referents", Fixture)
{
    auto referentFoo = f.add("foo");
    auto referentBar = f.add("bar");
    EXPECT_NOT_EQUAL(referentFoo.get(), referentBar.get());
    auto referent = f._registry.get("foo");
    EXPECT_EQUAL(referentFoo.get(), referent.get());
    referent = f._registry.get("bar");
    EXPECT_EQUAL(referentBar.get(), referent.get());
}

TEST_F("Test that we block get until related add is completed", Fixture)
{
    const IDocumentDBReferentRegistry &registry = f._registry;
    std::thread getFooThread(getFooTask, &registry);
    sleep(1);
    std::shared_ptr<IDocumentDBReferent> nullResult;
    EXPECT_EQUAL(nullResult.get(), checkFooResult().get());
    auto referentFoo = f.add("foo");
    std::shared_ptr<IDocumentDBReferent> checkResult;
    for (int retry = 0; retry < 60 && !checkResult; ++retry) {
        sleep(1);
        checkResult = checkFooResult();
    }
    EXPECT_EQUAL(referentFoo.get(), checkResult.get());
    getFooThread.join();
}

TEST_F("Test that tryGet method can fail", Fixture)
{
    auto referentFoo = f.add("foo");
    auto referent = f._registry.tryGet("foo");
    EXPECT_EQUAL(referentFoo.get(), referent.get());
    referent = f._registry.tryGet("bar");
    EXPECT_TRUE(referent.get() == nullptr);
}

}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
