// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>
#include <vespa/searchcore/proton/reference/document_db_reference_resolver.h>
#include <vespa/searchcore/proton/reference/gid_to_lid_change_listener.h>
#include <vespa/searchcore/proton/reference/i_document_db_reference.h>
#include <vespa/searchcore/proton/reference/i_document_db_reference_registry.h>
#include <vespa/searchcore/proton/reference/i_gid_to_lid_change_listener.h>
#include <vespa/searchcore/proton/test/mock_document_db_reference.h>
#include <vespa/searchcore/proton/test/mock_gid_to_lid_change_handler.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/imported_attribute_vector.h>
#include <vespa/searchlib/attribute/reference_attribute.h>
#include <vespa/searchlib/common/i_gid_to_lid_mapper.h>
#include <vespa/searchlib/common/i_gid_to_lid_mapper_factory.h>
#include <vespa/searchlib/test/mock_attribute_manager.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/datatype/referencedatatype.h>
#include <vespa/vespalib/util/monitored_refcount.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/config-imported-fields.h>

#include <vespa/log/log.h>
LOG_SETUP("document_db_reference_resolver_test");

using namespace document;
using namespace proton;
using namespace search::attribute;
using namespace search;
using proton::test::MockDocumentDBReference;
using search::attribute::test::MockAttributeManager;
using vespa::config::search::ImportedFieldsConfig;
using vespa::config::search::ImportedFieldsConfigBuilder;
using vespalib::MonitoredRefCount;
using vespalib::SequencedTaskExecutor;
using vespalib::ISequencedTaskExecutor;

const ReferenceAttribute *getReferenceAttribute(const IGidToLidChangeListener &listener) {
    auto mylistener = dynamic_cast<const GidToLidChangeListener *>(&listener);
    assert(mylistener);
    return mylistener->getReferenceAttribute().get();
}

struct MyGidToLidMapperFactory : public IGidToLidMapperFactory {
    using SP = std::shared_ptr<MyGidToLidMapperFactory>;
    std::unique_ptr<IGidToLidMapper> getMapper() const override {
        return std::unique_ptr<IGidToLidMapper>();
    }
};

using proton::test::MockGidToLidChangeHandler;
using AddEntry = MockGidToLidChangeHandler::AddEntry;
using RemoveEntry = MockGidToLidChangeHandler::RemoveEntry;

struct MyDocumentDBReference : public MockDocumentDBReference {
    using SP = std::shared_ptr<MyDocumentDBReference>;
    using AttributesMap = std::map<vespalib::string, AttributeVector::SP>;
    MyGidToLidMapperFactory::SP factory;
    AttributesMap attributes;
    std::shared_ptr<MockGidToLidChangeHandler> _gidToLidChangeHandler;

    MyDocumentDBReference(MyGidToLidMapperFactory::SP factory_,
                          std::shared_ptr<MockGidToLidChangeHandler> gidToLidChangeHandler) noexcept
        : factory(std::move(factory_)),
          _gidToLidChangeHandler(std::move(gidToLidChangeHandler))
    {
    }
    IGidToLidMapperFactory::SP getGidToLidMapperFactory() override {
        return factory;
    }
    std::shared_ptr<search::attribute::ReadableAttributeVector> getAttribute(vespalib::stringref name) override {
        auto itr = attributes.find(name);
        if (itr != attributes.end()) {
            return itr->second;
        } else {
            return std::shared_ptr<search::attribute::ReadableAttributeVector>();
        }
    }
    void addIntAttribute(vespalib::stringref name) {
        attributes[name] = AttributeFactory::createAttribute(name, Config(BasicType::INT32));
    }
    std::unique_ptr<GidToLidChangeRegistrator> makeGidToLidChangeRegistrator(const vespalib::string &docTypeName) override {
        return std::make_unique<GidToLidChangeRegistrator>(_gidToLidChangeHandler, docTypeName);
    }

    MockGidToLidChangeHandler &getGidToLidChangeHandler() {
        return *_gidToLidChangeHandler;
    }
    void removeAttribute(vespalib::stringref name) {
        attributes.erase(name);
    }
};

struct MyReferenceRegistry : public IDocumentDBReferenceRegistry {
    using ReferenceMap = std::map<vespalib::string, IDocumentDBReference::SP>;
    ReferenceMap map;
    IDocumentDBReference::SP get(vespalib::stringref name) const override {
        auto itr = map.find(name);
        ASSERT_TRUE(itr != map.end());
        return itr->second;
    }
    IDocumentDBReference::SP tryGet(vespalib::stringref name) const override {
        auto itr = map.find(name);
        if (itr != map.end()) {
            return itr->second;
        } else {
            return IDocumentDBReference::SP();
        }
    }
    void add(vespalib::stringref name, IDocumentDBReference::SP reference) override {
        map[name] = reference;
    }
    void remove(vespalib::stringref) override {}
};

struct MyAttributeManager : public MockAttributeManager {
    void addIntAttribute(const vespalib::string &name) {
        addAttribute(name, AttributeFactory::createAttribute(name, Config(BasicType::INT32)));
    }
    void addReferenceAttribute(const vespalib::string &name) {
        addAttribute(name, std::make_shared<ReferenceAttribute>(name));
    }
    const ReferenceAttribute *getReferenceAttribute(const vespalib::string &name) const {
        AttributeGuard::UP guard = getAttribute(name);
        auto *result = dynamic_cast<const ReferenceAttribute *>(guard->get());
        ASSERT_TRUE(result != nullptr);
        return result;
    }
};

struct DocumentModel {
    DocumentType parentDocType;
    ReferenceDataType refDataType;
    DocumentType parentDocType2;
    ReferenceDataType refDataType2;
    DocumentType parentDocType3;
    ReferenceDataType refDataType3;
    DocumentType childDocType;
    DocumentModel()
        : parentDocType("parent"),
          refDataType(parentDocType, 1234),
          parentDocType2("parent2"),
          refDataType2(parentDocType2, 1235),
          parentDocType3("parent3"),
          refDataType3(parentDocType3, 1236),
          childDocType("child")
    {
        initChildDocType();
    }
    ~DocumentModel();
    void initChildDocType() {
        childDocType.addField(Field("ref", refDataType));
        childDocType.addField(Field("other_ref", refDataType));
        childDocType.addField(Field("parent2_ref", refDataType2));
        childDocType.addField(Field("parent3_ref", refDataType3));
    }
};

DocumentModel::~DocumentModel() = default;

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
    auto *result = dynamic_cast<const ImportedAttributeVector *>(&attr);
    ASSERT_TRUE(result != nullptr);
    return *result;
}

VESPA_THREAD_STACK_TAG(attribute_executor)

struct Fixture {
    MyGidToLidMapperFactory::SP factory;
    MonitoredRefCount _gidToLidChangeListenerRefCount;
    std::unique_ptr<ISequencedTaskExecutor> _attributeFieldWriter;
    std::shared_ptr<MockGidToLidChangeHandler> _parentGidToLidChangeHandler;
    std::shared_ptr<MockGidToLidChangeHandler> _parentGidToLidChangeHandler2;
    MyDocumentDBReference::SP parentReference;
    MyDocumentDBReference::SP parentReference2;
    MyReferenceRegistry registry;
    MyAttributeManager attrMgr;
    MyAttributeManager oldAttrMgr;
    DocumentModel docModel;
    ImportedFieldsConfig importedFieldsCfg;
    Fixture() :
        factory(std::make_shared<MyGidToLidMapperFactory>()),
        _gidToLidChangeListenerRefCount(),
        _attributeFieldWriter(SequencedTaskExecutor::create(attribute_executor, 1)),
        _parentGidToLidChangeHandler(std::make_shared<MockGidToLidChangeHandler>()),
        _parentGidToLidChangeHandler2(std::make_shared<MockGidToLidChangeHandler>()),
        parentReference(std::make_shared<MyDocumentDBReference>(factory, _parentGidToLidChangeHandler)),
        parentReference2(std::make_shared<MyDocumentDBReference>(factory, _parentGidToLidChangeHandler2)),
        registry(),
        attrMgr(),
        docModel(),
        importedFieldsCfg(createImportedFieldsConfig())
    {
        registry.add("parent", parentReference);
        registry.add("parent2", parentReference2);
        populateTargetAttributes();
        populateAttributeManagers();
    }
    void populateTargetAttributes() {
        parentReference->addIntAttribute("target_a");
        parentReference->addIntAttribute("target_b");
    }
    void populateAttributeManagers() {
        attrMgr.addReferenceAttribute("ref");
        attrMgr.addReferenceAttribute("other_ref");
        attrMgr.addIntAttribute("int_attr");
        oldAttrMgr.addReferenceAttribute("parent2_ref");
        oldAttrMgr.addReferenceAttribute("parent3_ref");
    }
    ImportedAttributesRepo::UP resolve(vespalib::duration visibilityDelay, bool useReferences) {
        DocumentDBReferenceResolver resolver(registry, docModel.childDocType, importedFieldsCfg, docModel.childDocType, _gidToLidChangeListenerRefCount, *_attributeFieldWriter, useReferences);
        return resolver.resolve(attrMgr, oldAttrMgr, std::shared_ptr<search::IDocumentMetaStoreContext>(), visibilityDelay);
    }
    ImportedAttributesRepo::UP resolve(vespalib::duration visibilityDelay) {
        return resolve(visibilityDelay, true);
    }
    ImportedAttributesRepo::UP resolveReplay() {
        return resolve(vespalib::duration::zero(), false);
    }
    ImportedAttributesRepo::UP resolve() {
        return resolve(vespalib::duration::zero());
    }
    void teardown() {
        DocumentDBReferenceResolver resolver(registry, docModel.childDocType, importedFieldsCfg, docModel.childDocType, _gidToLidChangeListenerRefCount, *_attributeFieldWriter, false);
        resolver.teardown(attrMgr);
    }
    const IGidToLidMapperFactory *getMapperFactoryPtr(const vespalib::string &attrName) {
        return attrMgr.getReferenceAttribute(attrName)->getGidToLidMapperFactory().get();
    }
    void assertImportedAttribute(const vespalib::string &name,
                                 const vespalib::string &referenceField,
                                 const vespalib::string &targetField,
                                 bool useSearchCache,
                                 const ImportedAttributeVector::SP & attr) {
        ASSERT_TRUE(attr.get());
        EXPECT_EQUAL(name, attr->getName());
        EXPECT_EQUAL(attrMgr.getReferenceAttribute(referenceField), attr->getReferenceAttribute().get());
        EXPECT_EQUAL(parentReference->getAttribute(targetField).get(), attr->getTargetAttribute().get());
        EXPECT_EQUAL(useSearchCache, attr->getSearchCache().get() != nullptr);
    }

    MockGidToLidChangeHandler &getGidToLidChangeHandler(const vespalib::string &referencedDocTypeName) {
        auto ireference = registry.get(referencedDocTypeName);
        auto reference = std::dynamic_pointer_cast<MyDocumentDBReference>(ireference);
        assert(reference);
        return reference->getGidToLidChangeHandler();
    }

    void assertParentAdds(const vespalib::string &referencedDocTypeName, const std::vector<AddEntry> &expAdds)
    {
        auto &handler = getGidToLidChangeHandler(referencedDocTypeName);
        handler.assertAdds(expAdds);
    }

    void assertParentRemoves(const vespalib::string &referencedDocTypeName, const std::vector<RemoveEntry> &expRemoves)
    {
        auto &handler = getGidToLidChangeHandler(referencedDocTypeName);
        handler.assertRemoves(expRemoves);
    }
};

TEST_F("require that reference attributes are connected to gid mapper", Fixture)
{
    f.resolve();
    EXPECT_EQUAL(f.factory.get(), f.getMapperFactoryPtr("ref"));
    EXPECT_EQUAL(f.factory.get(), f.getMapperFactoryPtr("other_ref"));
}

TEST_F("require that reference attributes are not connected to gid mapper during replay", Fixture)
{
    f.resolveReplay();
    EXPECT_EQUAL(static_cast<IGidToLidMapperFactory *>(nullptr), f.getMapperFactoryPtr("ref"));
    EXPECT_EQUAL(static_cast<IGidToLidMapperFactory *>(nullptr), f.getMapperFactoryPtr("other_ref"));
}

TEST_F("require that imported attributes are instantiated without search cache as default", Fixture)
{
    auto repo = f.resolve();
    EXPECT_EQUAL(2u, repo->size());
    f.assertImportedAttribute("imported_a", "ref", "target_a", false, repo->get("imported_a"));
    f.assertImportedAttribute("imported_b", "other_ref", "target_b", false, repo->get("imported_b"));
}

TEST_F("require that imported attributes are instantiated with search cache if visibility delay > 0", Fixture)
{
    auto repo = f.resolve(1s);
    EXPECT_EQUAL(2u, repo->size());
    f.assertImportedAttribute("imported_a", "ref", "target_a", true, repo->get("imported_a"));
    f.assertImportedAttribute("imported_b", "other_ref", "target_b", true, repo->get("imported_b"));
}

TEST_F("require that missing target attribute prevents creation of imported attribute", Fixture)
{
    f.parentReference->removeAttribute("target_a");
    auto repo =  f.resolve();
    EXPECT_EQUAL(1u, repo->size());
    EXPECT_FALSE(repo->get("imported_a"));
    EXPECT_TRUE(repo->get("imported_b"));
}

TEST_F("require that listeners are added", Fixture)
{
    f.resolve();
    TEST_DO(f.assertParentAdds("parent", {{"child","other_ref"},{"child","ref"}}));
    TEST_DO(f.assertParentRemoves("parent", {{"child", {"other_ref","ref"}}}));
    auto &listeners = f.getGidToLidChangeHandler("parent").getListeners();
    EXPECT_EQUAL(2u, listeners.size());
    EXPECT_EQUAL(f.attrMgr.getReferenceAttribute("other_ref"), getReferenceAttribute(*listeners[0]));
    EXPECT_EQUAL(f.attrMgr.getReferenceAttribute("ref"), getReferenceAttribute(*listeners[1]));
    TEST_DO(f.assertParentAdds("parent2", {}));
    TEST_DO(f.assertParentRemoves("parent2", {{"child", {}}}));
}

TEST_F("require that listeners are removed", Fixture)
{
    f.teardown();
    TEST_DO(f.assertParentAdds("parent", {}));
    TEST_DO(f.assertParentRemoves("parent", {{"child", {}}}));
    TEST_DO(f.assertParentAdds("parent2", {}));
    TEST_DO(f.assertParentRemoves("parent2", {}));
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
