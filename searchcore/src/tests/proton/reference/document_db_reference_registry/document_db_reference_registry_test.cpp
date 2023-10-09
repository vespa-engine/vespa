// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchcore/proton/reference/document_db_reference_registry.h>
#include <vespa/searchcore/proton/test/mock_document_db_reference.h>
#include <thread>
#include <unistd.h>

#include <vespa/log/log.h>
LOG_SETUP("document_db_reference_registry_test");

namespace proton {

namespace {


std::mutex lock;
std::shared_ptr<IDocumentDBReference> getFooResult;

void getFooTask(const IDocumentDBReferenceRegistry *registry)
{
    auto result = registry->get("foo");
    std::lock_guard<std::mutex> guard(lock);
    getFooResult = result;
}

std::shared_ptr<IDocumentDBReference> checkFooResult()
{
    std::lock_guard<std::mutex> guard(lock);
    return getFooResult;
}

}

struct Fixture
{
    DocumentDBReferenceRegistry _registry;

    Fixture()
        : _registry()
    {
    }

    test::MockDocumentDBReference::SP
    add(vespalib::string name) {
        auto reference = std::make_shared<test::MockDocumentDBReference>();
        _registry.add(name, reference);
        return reference;
    }
};

TEST_F("Test that we can instantiate registry with two references", Fixture)
{
    auto referenceFoo = f.add("foo");
    auto referenceBar = f.add("bar");
    EXPECT_NOT_EQUAL(referenceFoo.get(), referenceBar.get());
    auto reference = f._registry.get("foo");
    EXPECT_EQUAL(referenceFoo.get(), reference.get());
    reference = f._registry.get("bar");
    EXPECT_EQUAL(referenceBar.get(), reference.get());
}

TEST_F("Test that we block get until related add is completed", Fixture)
{
    const IDocumentDBReferenceRegistry &registry = f._registry;
    std::thread getFooThread(getFooTask, &registry);
    sleep(1);
    std::shared_ptr<IDocumentDBReference> nullResult;
    EXPECT_EQUAL(nullResult.get(), checkFooResult().get());
    auto referenceFoo = f.add("foo");
    std::shared_ptr<IDocumentDBReference> checkResult;
    for (int retry = 0; retry < 60 && !checkResult; ++retry) {
        sleep(1);
        checkResult = checkFooResult();
    }
    EXPECT_EQUAL(referenceFoo.get(), checkResult.get());
    getFooThread.join();
}

TEST_F("Test that tryGet method can fail", Fixture)
{
    auto referenceFoo = f.add("foo");
    auto reference = f._registry.tryGet("foo");
    EXPECT_EQUAL(referenceFoo.get(), reference.get());
    reference = f._registry.tryGet("bar");
    EXPECT_TRUE(reference.get() == nullptr);
}

}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
