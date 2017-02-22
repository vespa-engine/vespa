// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("imported_attributes_repo_test");
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>
#include <vespa/searchlib/attribute/not_implemented_attribute.h>
#include <vespa/searchcommon/attribute/basictype.h>

using proton::ImportedAttributesRepo;
using search::attribute::BasicType;
using search::attribute::Config;
using search::attribute::IAttributeVector;
using search::NotImplementedAttribute;

struct MyAttributeVector : public NotImplementedAttribute {
private:
    virtual void onCommit() override {}
    virtual void onUpdateStat() override {}

public:
    MyAttributeVector(const vespalib::string &name)
        : NotImplementedAttribute(name, Config(BasicType::NONE))
    {}
    static IAttributeVector::SP create(const vespalib::string &name) {
        return std::make_shared<MyAttributeVector>(name);
    }
};

struct Fixture {
    ImportedAttributesRepo repo;
    Fixture() : repo() {}
    void add(IAttributeVector::SP attr) {
        repo.add(attr->getName(), attr);
    }
    IAttributeVector::SP get(const vespalib::string &name) const {
        return repo.get(name);
    }
};

TEST_F("require that attributes can be added and retrieved", Fixture)
{
    IAttributeVector::SP fooAttr = MyAttributeVector::create("foo");
    IAttributeVector::SP barAttr = MyAttributeVector::create("bar");
    f.add(fooAttr);
    f.add(barAttr);
    EXPECT_EQUAL(f.get("foo").get(), fooAttr.get());
    EXPECT_EQUAL(f.get("bar").get(), barAttr.get());
}

TEST_F("require that attribute can be replaced", Fixture)
{
    IAttributeVector::SP attr1 = MyAttributeVector::create("foo");
    IAttributeVector::SP attr2 = MyAttributeVector::create("foo");
    f.add(attr1);
    f.add(attr2);
    EXPECT_EQUAL(f.get("foo").get(), attr2.get());
}

TEST_F("require that not-found attribute returns nullptr", Fixture)
{
    IAttributeVector *notFound = nullptr;
    EXPECT_EQUAL(f.get("not_found").get(), notFound);
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
