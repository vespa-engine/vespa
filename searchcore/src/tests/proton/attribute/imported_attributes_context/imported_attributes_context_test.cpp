// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/attribute/imported_attributes_context.h>
#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastorecontext.h>
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/searchlib/attribute/attribute.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/imported_attribute_vector.h>
#include <vespa/searchlib/attribute/imported_attribute_vector_factory.h>
#include <vespa/searchlib/attribute/reference_attribute.h>
#include <vespa/searchlib/test/mock_gid_to_lid_mapping.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <cassert>
#include <future>

#include <vespa/log/log.h>
LOG_SETUP("imported_attributes_context_test");
using namespace proton;
using search::AttributeVector;
using search::attribute::BasicType;
using search::attribute::Config;
using search::attribute::IAttributeVector;
using search::attribute::ImportedAttributeVector;
using search::attribute::ImportedAttributeVectorFactory;
using search::attribute::ReferenceAttribute;
using search::attribute::test::MockGidToLidMapperFactory;
using generation_t = AttributeVector::generation_t;

std::shared_ptr<ReferenceAttribute>
createReferenceAttribute(const std::string &name)
{
    auto refAttr = std::make_shared<ReferenceAttribute>(name);
    refAttr->setGidToLidMapperFactory(std::make_shared<MockGidToLidMapperFactory>());
    return refAttr;
}

AttributeVector::SP
createTargetAttribute(const std::string &name)
{
    return search::AttributeFactory::createAttribute(name, Config(BasicType::STRING));
}

void
addDoc(AttributeVector &attr)
{
    attr.addDocs(1);
    attr.commit();
}

bool
hasActiveEnumGuards(AttributeVector &attr)
{
    return std::async(std::launch::async, [&attr] { return attr.hasActiveEnumGuards(); }).get();
}

void
assertGuards(AttributeVector &attr, generation_t expCurrentGeneration, generation_t exp_oldest_used_generation,
             bool expHasActiveEnumGuards)
{
    EXPECT_EQ(expCurrentGeneration, attr.getCurrentGeneration());
    EXPECT_EQ(exp_oldest_used_generation, attr.get_oldest_used_generation());
    EXPECT_EQ(expHasActiveEnumGuards, hasActiveEnumGuards(attr));
}

void
addDocAndAssertGuards(std::string_view label, AttributeVector &attr, generation_t expCurrentGeneration, generation_t expFirstUsedGeneration, bool expHasActiveEnumGuards)
{
    SCOPED_TRACE(label);
    addDoc(attr);
    assertGuards(attr, expCurrentGeneration, expFirstUsedGeneration, expHasActiveEnumGuards);
}

class ImportedAttributesContextTest : public ::testing::Test {
protected:
    ImportedAttributesRepo repo;
    std::unique_ptr<ImportedAttributesContext> ctx;
    ImportedAttributesContextTest();
    ~ImportedAttributesContextTest() override;
    void addAttribute(const std::string &name) {
        auto attr = ImportedAttributeVectorFactory::create(name,
                                                           createReferenceAttribute(name + "_ref"),
                                                           std::shared_ptr<search::IDocumentMetaStoreContext>(),
                                                           createTargetAttribute(name + "_target"),
                                                           std::make_shared<const DocumentMetaStoreContext>(std::make_shared<bucketdb::BucketDBOwner>()),
                                                           false);
        repo.add(name, attr);
    }
    AttributeVector::SP getTargetAttribute(const std::string &importedName) const {
        auto readable_target_attr = repo.get(importedName)->getTargetAttribute();
        auto target_attr = std::dynamic_pointer_cast<AttributeVector>(readable_target_attr);
        assert(target_attr);
        return target_attr;
    }
    void clearContext() {
        ctx.reset();
    }
};

ImportedAttributesContextTest::ImportedAttributesContextTest()
    : repo(),
      ctx(std::make_unique<ImportedAttributesContext>(repo))
{
}

ImportedAttributesContextTest::~ImportedAttributesContextTest() = default;

TEST_F(ImportedAttributesContextTest, require_that_attributes_can_be_retrieved)
{
    addAttribute("foo");
    addAttribute("bar");
    EXPECT_EQ("foo", ctx->getAttribute("foo")->getName());
    EXPECT_EQ("bar", ctx->getAttribute("bar")->getName());
    EXPECT_EQ("bar", ctx->getAttribute("bar")->getName());
    EXPECT_TRUE(ctx->getAttribute("not_found") == nullptr);
}

TEST_F(ImportedAttributesContextTest, require_that_stable_enum_attributes_can_be_retrieved)
{
    addAttribute("foo");
    addAttribute("bar");
    EXPECT_EQ("foo", ctx->getAttributeStableEnum("foo")->getName());
    EXPECT_EQ("bar", ctx->getAttributeStableEnum("bar")->getName());
    EXPECT_EQ("bar", ctx->getAttributeStableEnum("bar")->getName());
    EXPECT_TRUE(ctx->getAttributeStableEnum("not_found") == nullptr);
}

TEST_F(ImportedAttributesContextTest, require_that_all_attributes_can_be_retrieved)
{
    addAttribute("foo");
    addAttribute("bar");
    std::vector<const IAttributeVector *> list;
    ctx->getAttributeList(list);
    EXPECT_EQ(2u, list.size());
    // Don't depend on internal (unspecified) ordering
    std::sort(list.begin(), list.end(), [](auto* lhs, auto* rhs){
        return lhs->getName() < rhs->getName();
    });
    EXPECT_EQ("bar", list[0]->getName());
    EXPECT_EQ("foo", list[1]->getName());
}

TEST_F(ImportedAttributesContextTest, require_that_guards_are_cached)
{
    addAttribute("foo");
    auto targetAttr = getTargetAttribute("foo");
    addDocAndAssertGuards("first", *targetAttr, 2, 2, false);

    ctx->getAttribute("foo"); // guard is taken and cached
    addDocAndAssertGuards("second", *targetAttr, 4, 2, false);

    clearContext(); // guard is released
    addDocAndAssertGuards("third", *targetAttr, 6, 6, false);
}

TEST_F(ImportedAttributesContextTest, require_that_stable_enum_guards_are_cached)
{
    addAttribute("foo");
    auto targetAttr = getTargetAttribute("foo");
    addDocAndAssertGuards("first", *targetAttr, 2, 2, false);

    ctx->getAttributeStableEnum("foo"); // enum guard is taken and cached
    addDocAndAssertGuards("second", *targetAttr, 4, 2, true);

    clearContext(); // guard is released
    addDocAndAssertGuards("third", *targetAttr, 6, 6, false);
}

TEST_F(ImportedAttributesContextTest, require_that_stable_enum_guards_can_be_released)
{
    addAttribute("foo");
    auto targetAttr = getTargetAttribute("foo");
    addDocAndAssertGuards("first", *targetAttr, 2, 2, false);

    ctx->getAttributeStableEnum("foo"); // enum guard is taken and cached
    addDocAndAssertGuards("second", *targetAttr, 4, 2, true);

    ctx->releaseEnumGuards();
    addDocAndAssertGuards("third", *targetAttr, 6, 6, false);
}

GTEST_MAIN_RUN_ALL_TESTS()
