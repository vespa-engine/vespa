// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>
#include <vespa/searchcore/proton/reference/document_db_reference.h>
#include <vespa/searchcore/proton/test/mock_attribute_manager.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/imported_attribute_vector.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <cassert>
#include <vespa/log/log.h>
LOG_SETUP("document_db_reference_test");

using namespace proton;

using search::AttributeFactory;
using search::AttributeVector;
using search::IDocumentMetaStoreContext;
using search::attribute::BasicType;
using search::attribute::ImportedAttributeVector;
using search::attribute::ReadableAttributeVector;
using search::attribute::ReferenceAttribute;

ImportedAttributesRepo::UP
makeImportedAttributesRepo()
{
    ImportedAttributesRepo::UP result = std::make_unique<ImportedAttributesRepo>();
    ImportedAttributeVector::SP attr = std::make_shared<ImportedAttributeVector>("imported",
                                                                                 std::shared_ptr<ReferenceAttribute>(),
                                                                                 std::shared_ptr<IDocumentMetaStoreContext>(),
                                                                                 std::shared_ptr<ReadableAttributeVector>(),
                                                                                 std::shared_ptr<const IDocumentMetaStoreContext>(), false);
    result->add("imported", std::move(attr));
    return result;
}

struct DocumentDBReferenceTest : public ::testing::Test {
    std::shared_ptr<test::MockAttributeManager> attrMgr;
    DocumentDBReference ref;
    DocumentDBReferenceTest();
    ~DocumentDBReferenceTest() override;
};

DocumentDBReferenceTest::DocumentDBReferenceTest()
    : attrMgr(std::make_shared<test::MockAttributeManager>()),
      ref(attrMgr, std::shared_ptr<const IDocumentMetaStoreContext>(), std::shared_ptr<IGidToLidChangeHandler>())
{
    attrMgr->addAttribute("regular", AttributeFactory::createAttribute("regular", {BasicType::INT32}));
    attrMgr->setImportedAttributes(makeImportedAttributesRepo());
}

DocumentDBReferenceTest::~DocumentDBReferenceTest() = default;

TEST_F(DocumentDBReferenceTest, regular_attribute_vector_can_be_retrieved)
{
    auto attr = ref.getAttribute("regular");
    EXPECT_TRUE(attr.get());
    const AttributeVector *attrPtr = dynamic_cast<const AttributeVector *>(attr.get());
    EXPECT_TRUE(attrPtr != nullptr);
}

TEST_F(DocumentDBReferenceTest, imported_attribute_vector_can_be_retrieved)
{
    auto attr = ref.getAttribute("imported");
    EXPECT_TRUE(attr.get());
    const ImportedAttributeVector *importedPtr = dynamic_cast<const ImportedAttributeVector *>(attr.get());
    EXPECT_TRUE(importedPtr != nullptr);
}

TEST_F(DocumentDBReferenceTest, nullptr_is_returned_for_non_existing_attribute_vector)
{
    auto attr = ref.getAttribute("non-existing");
    EXPECT_FALSE(attr.get());
}

GTEST_MAIN_RUN_ALL_TESTS()
