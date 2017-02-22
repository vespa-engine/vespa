// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/datatype/referencedatatype.h>
#include <vespa/log/log.h>
#include <vespa/searchcore/proton/reference/document_db_reference_resolver.h>
#include <vespa/searchcore/proton/reference/i_document_db_referent.h>
#include <vespa/searchcore/proton/reference/i_document_db_referent_registry.h>
#include <vespa/searchcore/proton/test/mock_document_db_referent.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/reference_attribute.h>
#include <vespa/searchlib/common/i_gid_to_lid_mapper.h>
#include <vespa/searchlib/common/i_gid_to_lid_mapper_factory.h>
#include <vespa/searchlib/test/mock_attribute_manager.h>

LOG_SETUP("document_db_reference_resolver_test");

using namespace document;
using namespace proton;
using namespace search::attribute;
using namespace search;
using proton::test::MockDocumentDBReferent;
using search::attribute::test::MockAttributeManager;

struct MyGidToLidMapperFactory : public IGidToLidMapperFactory {
    using SP = std::shared_ptr<MyGidToLidMapperFactory>;
    virtual std::unique_ptr<IGidToLidMapper> getMapper() const override {
        return std::unique_ptr<IGidToLidMapper>();
    }
};

struct MyDocumentDBReferent : public MockDocumentDBReferent {
    MyGidToLidMapperFactory::SP factory;
    MyDocumentDBReferent(MyGidToLidMapperFactory::SP factory_) : factory(factory_) {}
    virtual IGidToLidMapperFactory::SP getGidToLidMapperFactory() override {
        return factory;
    }
};

struct MyReferentRegistry : public IDocumentDBReferentRegistry {
    using ReferentMap = std::map<vespalib::string, IDocumentDBReferent::SP>;
    ReferentMap map;
    virtual IDocumentDBReferent::SP get(vespalib::stringref name) const override {
        auto itr = map.find(name);
        if (itr != map.end()) {
            return itr->second;
        }
        return IDocumentDBReferent::SP();
    }
    virtual void add(vespalib::stringref name, IDocumentDBReferent::SP referent) override {
        map[name] = referent;
    }
    virtual void remove(vespalib::stringref) override {}
};

struct MyAttributeManager : public MockAttributeManager {
    void addIntAttribute(const vespalib::string &name) {
        addAttribute(name, AttributeFactory::createAttribute(name, Config(BasicType::INT32)));
    }
    void addReferenceAttribute(const vespalib::string &name) {
        addAttribute(name, std::make_shared<ReferenceAttribute>(name, Config(BasicType::REFERENCE)));
    }
    const ReferenceAttribute *getReferenceAttribute(const vespalib::string &name) const {
        AttributeGuard::UP guard = getAttribute(name);
        const ReferenceAttribute *result = dynamic_cast<const ReferenceAttribute *>(&guard->get());
        ASSERT_TRUE(result != nullptr);
        return result;
    }
};

struct DocumentModel {
    DocumentType parentDocType;
    ReferenceDataType refDataType;
    DocumentType childDocType;
    DocumentModel()
        : parentDocType("parent"),
          refDataType(parentDocType, 1234),
          childDocType("child")
    {
        initChildDocType();
    }
    void initChildDocType() {
        childDocType.addField(Field("ref", refDataType, true));
        childDocType.addField(Field("other_ref", refDataType, true));
    }
};

struct Fixture {
    MyGidToLidMapperFactory::SP factory;
    MyDocumentDBReferent::SP parentReferent;
    MyReferentRegistry registry;
    MyAttributeManager attrMgr;
    DocumentModel docModel;
    DocumentDBReferenceResolver resolver;
    Fixture() :
        factory(std::make_shared<MyGidToLidMapperFactory>()),
        parentReferent(std::make_shared<MyDocumentDBReferent>(factory)),
        registry(),
        attrMgr(),
        docModel(),
        resolver(registry, attrMgr, docModel.childDocType)
    {
        registry.add("parent", parentReferent);
        populateAttributeManager();
    }
    void populateAttributeManager() {
        attrMgr.addReferenceAttribute("ref");
        attrMgr.addReferenceAttribute("other_ref");
        attrMgr.addIntAttribute("int_attr");
    }
    void resolve() {
        resolver.resolve();
    }
    const IGidToLidMapperFactory *getMapperFactoryPtr(const vespalib::string &attrName) {
        return attrMgr.getReferenceAttribute(attrName)->getGidToLidMapperFactory().get();
    }
};

TEST_F("require that reference attributes are connected to gid mapper", Fixture)
{
    f.resolve();
    EXPECT_EQUAL(f.factory.get(), f.getMapperFactoryPtr("ref"));
    EXPECT_EQUAL(f.factory.get(), f.getMapperFactoryPtr("other_ref"));
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
