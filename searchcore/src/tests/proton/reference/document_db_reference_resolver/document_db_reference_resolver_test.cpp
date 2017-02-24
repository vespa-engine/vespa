// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/config-imported-fields.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/datatype/referencedatatype.h>
#include <vespa/log/log.h>
#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>
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
using vespa::config::search::ImportedFieldsConfig;
using vespa::config::search::ImportedFieldsConfigBuilder;

struct MyGidToLidMapperFactory : public IGidToLidMapperFactory {
    using SP = std::shared_ptr<MyGidToLidMapperFactory>;
    virtual std::unique_ptr<IGidToLidMapper> getMapper() const override {
        return std::unique_ptr<IGidToLidMapper>();
    }
};

struct MyDocumentDBReferent : public MockDocumentDBReferent {
    using SP = std::shared_ptr<MyDocumentDBReferent>;
    using AttributesMap = std::map<vespalib::string, AttributeVector::SP>;
    MyGidToLidMapperFactory::SP factory;
    AttributesMap attributes;

    MyDocumentDBReferent(MyGidToLidMapperFactory::SP factory_) : factory(factory_) {}
    virtual IGidToLidMapperFactory::SP getGidToLidMapperFactory() override {
        return factory;
    }
    virtual AttributeVector::SP getAttribute(vespalib::stringref name) override {
        auto itr = attributes.find(name);
        ASSERT_TRUE(itr != attributes.end());
        return itr->second;
    }
    void addIntAttribute(vespalib::stringref name) {
        attributes[name] = AttributeFactory::createAttribute(name, Config(BasicType::INT32));
    }
};

struct MyReferentRegistry : public IDocumentDBReferentRegistry {
    using ReferentMap = std::map<vespalib::string, IDocumentDBReferent::SP>;
    ReferentMap map;
    virtual IDocumentDBReferent::SP get(vespalib::stringref name) const override {
        auto itr = map.find(name);
        ASSERT_TRUE(itr != map.end());
        return itr->second;
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
        const ReferenceAttribute *result = dynamic_cast<const ReferenceAttribute *>(guard->get());
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

void
set(const vespalib::string &name,
    const vespalib::string &referenceField,
    const vespalib::string &targetField,
    ImportedFieldsConfigBuilder::Attribute &attr)
{
    attr.name = name;
    attr.referencefield = referenceField;
    attr.targetfield = targetField;
}

ImportedFieldsConfig
createImportedFieldsConfig()
{
    ImportedFieldsConfigBuilder builder;
    builder.attribute.resize(2);
    set("imported_a", "ref", "target_a", builder.attribute[0]);
    set("imported_b", "other_ref", "target_b", builder.attribute[1]);
    return builder;
}

const ImportedAttributeVector &
asImportedAttribute(const IAttributeVector &attr)
{
    const ImportedAttributeVector *result = dynamic_cast<const ImportedAttributeVector *>(&attr);
    ASSERT_TRUE(result != nullptr);
    return *result;
}

struct Fixture {
    MyGidToLidMapperFactory::SP factory;
    MyDocumentDBReferent::SP parentReferent;
    MyReferentRegistry registry;
    MyAttributeManager attrMgr;
    DocumentModel docModel;
    ImportedFieldsConfig importedFieldsCfg;
    DocumentDBReferenceResolver resolver;
    Fixture() :
        factory(std::make_shared<MyGidToLidMapperFactory>()),
        parentReferent(std::make_shared<MyDocumentDBReferent>(factory)),
        registry(),
        attrMgr(),
        docModel(),
        importedFieldsCfg(createImportedFieldsConfig()),
        resolver(registry, docModel.childDocType, importedFieldsCfg)
    {
        registry.add("parent", parentReferent);
        populateTargetAttributes();
        populateAttributeManager();
    }
    void populateTargetAttributes() {
        parentReferent->addIntAttribute("target_a");
        parentReferent->addIntAttribute("target_b");
    }
    void populateAttributeManager() {
        attrMgr.addReferenceAttribute("ref");
        attrMgr.addReferenceAttribute("other_ref");
        attrMgr.addIntAttribute("int_attr");
    }
    ImportedAttributesRepo::UP resolve() {
        return resolver.resolve(attrMgr);
    }
    const IGidToLidMapperFactory *getMapperFactoryPtr(const vespalib::string &attrName) {
        return attrMgr.getReferenceAttribute(attrName)->getGidToLidMapperFactory().get();
    }
    void assertImportedAttribute(const vespalib::string &name,
                                 const vespalib::string &referenceField,
                                 const vespalib::string &targetField,
                                 IAttributeVector::SP attr) {
        ASSERT_TRUE(attr.get());
        EXPECT_EQUAL(name, attr->getName());
        const ImportedAttributeVector &importedAttr = asImportedAttribute(*attr);
        EXPECT_EQUAL(attrMgr.getReferenceAttribute(referenceField), importedAttr.getReferenceAttribute().get());
        EXPECT_EQUAL(parentReferent->getAttribute(targetField).get(), importedAttr.getTargetAttribute().get());
    }
};

TEST_F("require that reference attributes are connected to gid mapper", Fixture)
{
    f.resolve();
    EXPECT_EQUAL(f.factory.get(), f.getMapperFactoryPtr("ref"));
    EXPECT_EQUAL(f.factory.get(), f.getMapperFactoryPtr("other_ref"));
}

TEST_F("require that imported attributes are instantiated", Fixture)
{
    auto repo = f.resolve();
    EXPECT_EQUAL(2u, repo->size());
    f.assertImportedAttribute("imported_a", "ref", "target_a", repo->get("imported_a"));
    f.assertImportedAttribute("imported_b", "other_ref", "target_b", repo->get("imported_b"));
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
