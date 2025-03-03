// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/reference/document_db_reference_registry.h>
#include <vespa/searchcore/proton/test/mock_document_db_reference.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <thread>
#include <unistd.h>
#include <string>

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

struct DocumentDBReferenceRegistryTest : public ::testing::Test
{
    DocumentDBReferenceRegistry _registry;

    DocumentDBReferenceRegistryTest();
    ~DocumentDBReferenceRegistryTest() override;

    test::MockDocumentDBReference::SP
    add(std::string name) {
        auto reference = std::make_shared<test::MockDocumentDBReference>();
        _registry.add(name, reference);
        return reference;
    }
};

DocumentDBReferenceRegistryTest::DocumentDBReferenceRegistryTest()
    : ::testing::Test(),
      _registry()
{
}

DocumentDBReferenceRegistryTest::~DocumentDBReferenceRegistryTest() = default;

TEST_F(DocumentDBReferenceRegistryTest, Test_that_we_can_instantiate_registry_with_two_references)
{
    auto referenceFoo = add("foo");
    auto referenceBar = add("bar");
    EXPECT_NE(referenceFoo.get(), referenceBar.get());
    auto reference = _registry.get("foo");
    EXPECT_EQ(referenceFoo.get(), reference.get());
    reference = _registry.get("bar");
    EXPECT_EQ(referenceBar.get(), reference.get());
}

TEST_F(DocumentDBReferenceRegistryTest, Test_that_we_block_get_until_related_add_is_completed)
{
    const IDocumentDBReferenceRegistry &registry = _registry;
    std::thread getFooThread(getFooTask, &registry);
    sleep(1);
    std::shared_ptr<IDocumentDBReference> nullResult;
    EXPECT_EQ(nullResult.get(), checkFooResult().get());
    auto referenceFoo = add("foo");
    std::shared_ptr<IDocumentDBReference> checkResult;
    for (int retry = 0; retry < 60 && !checkResult; ++retry) {
        sleep(1);
        checkResult = checkFooResult();
    }
    EXPECT_EQ(referenceFoo.get(), checkResult.get());
    getFooThread.join();
}

TEST_F(DocumentDBReferenceRegistryTest, Test_that_tryGet_method_can_fail)
{
    auto referenceFoo = add("foo");
    auto reference = _registry.tryGet("foo");
    EXPECT_EQ(referenceFoo.get(), reference.get());
    reference = _registry.tryGet("bar");
    EXPECT_TRUE(reference.get() == nullptr);
}

}

GTEST_MAIN_RUN_ALL_TESTS()
