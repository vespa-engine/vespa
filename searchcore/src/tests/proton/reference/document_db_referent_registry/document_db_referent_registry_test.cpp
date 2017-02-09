// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchcore/proton/reference/document_db_referent_registry.h>
#include <vespa/searchcore/proton/reference/i_document_db_referent.h>
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
    auto result = registry->getDocumentDBReferent("foo");
    std::lock_guard<std::mutex> guard(lock);
    getFooResult = result;
}

std::shared_ptr<IDocumentDBReferent> checkFooResult()
{
    std::lock_guard<std::mutex> guard(lock);
    return getFooResult;
}

}

struct MyDocumentDBReferent : public IDocumentDBReferent
{
    MyDocumentDBReferent()
    {
    }
    virtual ~MyDocumentDBReferent() { }
    virtual std::shared_ptr<search::AttributeVector> getAttribute(vespalib::stringref name) override {
        (void) name;
        return std::shared_ptr<search::AttributeVector>();
    }
    virtual std::shared_ptr<search::IGidToLidMapperFactory> getGidToLidMapperFactory() override {
        return std::shared_ptr<search::IGidToLidMapperFactory>();
    }
};

struct Fixture
{

    DocumentDBReferentRegistry _registry;

    Fixture()
        : _registry()
    {
    }

    std::shared_ptr<MyDocumentDBReferent>
    add(vespalib::string name) {
        auto referent = std::make_shared<MyDocumentDBReferent>();
        _registry.addDocumentDBReferent(name, referent);
        return referent;
    }
};

TEST_F("Test that we can instantiate registry with two referents", Fixture)
{
    auto referentFoo = f.add("foo");
    auto referentBar = f.add("bar");
    EXPECT_NOT_EQUAL(referentFoo.get(), referentBar.get());
    auto referent = f._registry.getDocumentDBReferent("foo");
    EXPECT_EQUAL(referentFoo.get(), referent.get());
    referent = f._registry.getDocumentDBReferent("bar");
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

}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
