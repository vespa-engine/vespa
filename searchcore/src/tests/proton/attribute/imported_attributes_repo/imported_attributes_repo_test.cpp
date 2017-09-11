// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("imported_attributes_repo_test");
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/searchcommon/attribute/basictype.h>
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>
#include <vespa/searchlib/attribute/imported_attribute_vector.h>
#include <vespa/searchlib/attribute/reference_attribute.h>

using proton::ImportedAttributesRepo;
using search::AttributeVector;
using search::attribute::BasicType;
using search::attribute::Config;
using search::attribute::ImportedAttributeVector;
using search::attribute::ReferenceAttribute;

ImportedAttributeVector::SP
createAttr(const vespalib::string &name)
{
    return std::make_shared<ImportedAttributeVector>(name,
                                                     ReferenceAttribute::SP(),
                                                     AttributeVector::SP(),
                                                     std::shared_ptr<search::IDocumentMetaStoreContext>(),
                                                     false);
}

struct Fixture {
    ImportedAttributesRepo repo;
    Fixture() : repo() {}
    void add(ImportedAttributeVector::SP attr) {
        repo.add(attr->getName(), attr);
    }
    ImportedAttributeVector::SP get(const vespalib::string &name) const {
        return repo.get(name);
    }
};

TEST_F("require that attributes can be added and retrieved", Fixture)
{
    ImportedAttributeVector::SP fooAttr = createAttr("foo");
    ImportedAttributeVector::SP barAttr = createAttr("bar");
    f.add(fooAttr);
    f.add(barAttr);
    EXPECT_EQUAL(2u, f.repo.size());
    EXPECT_EQUAL(f.get("foo").get(), fooAttr.get());
    EXPECT_EQUAL(f.get("bar").get(), barAttr.get());
}

TEST_F("require that attribute can be replaced", Fixture)
{
    ImportedAttributeVector::SP attr1 = createAttr("foo");
    ImportedAttributeVector::SP attr2 = createAttr("foo");
    f.add(attr1);
    f.add(attr2);
    EXPECT_EQUAL(1u, f.repo.size());
    EXPECT_EQUAL(f.get("foo").get(), attr2.get());
}

TEST_F("require that not-found attribute returns nullptr", Fixture)
{
    ImportedAttributeVector *notFound = nullptr;
    EXPECT_EQUAL(f.get("not_found").get(), notFound);
}

TEST_F("require that all attributes can be retrieved", Fixture)
{
    f.add(createAttr("foo"));
    f.add(createAttr("bar"));
    std::vector<ImportedAttributeVector::SP> list;
    f.repo.getAll(list);
    EXPECT_EQUAL(2u, list.size());
    EXPECT_EQUAL("bar", list[0]->getName());
    EXPECT_EQUAL("foo", list[1]->getName());
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
