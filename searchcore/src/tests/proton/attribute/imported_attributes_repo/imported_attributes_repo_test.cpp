// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/attribute/basictype.h>
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>
#include <vespa/searchlib/attribute/imported_attribute_vector.h>
#include <vespa/searchlib/attribute/imported_attribute_vector_factory.h>
#include <vespa/searchlib/attribute/reference_attribute.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <algorithm>

using proton::ImportedAttributesRepo;
using search::AttributeVector;
using search::attribute::BasicType;
using search::attribute::Config;
using search::attribute::ImportedAttributeVector;
using search::attribute::ImportedAttributeVectorFactory;
using search::attribute::ReferenceAttribute;

ImportedAttributeVector::SP
createAttr(const std::string &name)
{
    return ImportedAttributeVectorFactory::create(name,
                                                  ReferenceAttribute::SP(),
                                                  std::shared_ptr<search::IDocumentMetaStoreContext>(),
                                                  AttributeVector::SP(),
                                                  std::shared_ptr<const search::IDocumentMetaStoreContext>(),
                                                  false);
}

class ImportedAttributesRepoTest : public ::testing::Test {
protected:
    ImportedAttributesRepo repo;
    ImportedAttributesRepoTest();
    ~ImportedAttributesRepoTest() override;
    void add(ImportedAttributeVector::SP attr) {
        repo.add(attr->getName(), attr);
    }
    ImportedAttributeVector::SP get(const std::string &name) const {
        return repo.get(name);
    }
};

ImportedAttributesRepoTest::ImportedAttributesRepoTest()
    : ::testing::Test(),
      repo()
{
}

ImportedAttributesRepoTest::~ImportedAttributesRepoTest() = default;

TEST_F(ImportedAttributesRepoTest, require_that_attributes_can_be_added_and_retrieved)
{
    ImportedAttributeVector::SP fooAttr = createAttr("foo");
    ImportedAttributeVector::SP barAttr = createAttr("bar");
    add(fooAttr);
    add(barAttr);
    EXPECT_EQ(2u, repo.size());
    EXPECT_EQ(get("foo").get(), fooAttr.get());
    EXPECT_EQ(get("bar").get(), barAttr.get());
}

TEST_F(ImportedAttributesRepoTest, require_that_attribute_can_be_replaced)
{
    ImportedAttributeVector::SP attr1 = createAttr("foo");
    ImportedAttributeVector::SP attr2 = createAttr("foo");
    add(attr1);
    add(attr2);
    EXPECT_EQ(1u, repo.size());
    EXPECT_EQ(get("foo").get(), attr2.get());
}

TEST_F(ImportedAttributesRepoTest, require_that_not_found_attribute_returns_nullptr)
{
    ImportedAttributeVector *notFound = nullptr;
    EXPECT_EQ(get("not_found").get(), notFound);
}

TEST_F(ImportedAttributesRepoTest, require_that_all_attributes_can_be_retrieved)
{
    add(createAttr("foo"));
    add(createAttr("bar"));
    std::vector<ImportedAttributeVector::SP> list;
    repo.getAll(list);
    EXPECT_EQ(2u, list.size());
    // Don't depend on internal (unspecified) ordering
    std::sort(list.begin(), list.end(), [](auto& lhs, auto& rhs){
        return lhs->getName() < rhs->getName();
    });
    EXPECT_EQ("bar", list[0]->getName());
    EXPECT_EQ("foo", list[1]->getName());
}

GTEST_MAIN_RUN_ALL_TESTS()
