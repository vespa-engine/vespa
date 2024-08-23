// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/monitored_refcount.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>
#include <vespa/config-imported-fields.h>
#include <cassert>

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
        return {};
    }
};

using proton::test::MockGidToLidChangeHandler;
using AddEntry = MockGidToLidChangeHandler::AddEntry;
using RemoveEntry = MockGidToLidChangeHandler::RemoveEntry;

struct MyDocumentDBReference : public MockDocumentDBReference {
    using SP = std::shared_ptr<MyDocumentDBReference>;
    using AttributesMap = std::map<std::string, AttributeVector::SP>;
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
    std::shared_ptr<search::attribute::ReadableAttributeVector> getAttribute(std::string_view name) override {
        auto itr = attributes.find(std::string(name));
        if (itr != attributes.end()) {
            return itr->second;
        } else {
            return {};
        }
    }
    void addIntAttribute(std::string_view name) {
        attributes[std::string(name)] = AttributeFactory::createAttribute(name, Config(BasicType::INT32));
    }
    std::unique_ptr<GidToLidChangeRegistrator> makeGidToLidChangeRegistrator(const std::string &docTypeName) override {
        return std::make_unique<GidToLidChangeRegistrator>(_gidToLidChangeHandler, docTypeName);
    }

    MockGidToLidChangeHandler &getGidToLidChangeHandler() {
        return *_gidToLidChangeHandler;
    }
    void removeAttribute(std::string_view name) {
        attributes.erase(std::string(name));
    }
};

struct MyReferenceRegistry : public IDocumentDBReferenceRegistry {
    using ReferenceMap = std::map<std::string, IDocumentDBReference::SP>;
    ReferenceMap map;
    IDocumentDBReference::SP get(std::string_view name) const override {
        auto itr = map.find(std::string(name));
        assert(itr != map.end());
        return itr->second;
    }
    IDocumentDBReference::SP tryGet(std::string_view name) const override {
        auto itr = map.find(std::string(name));
        if (itr != map.end()) {
            return itr->second;
        } else {
            return {};
        }
    }
    void add(std::string_view name, IDocumentDBReference::SP reference) override {
        map[std::string(name)] = reference;
    }
    void remove(std::string_view) override {}
};

struct MyAttributeManager : public MockAttributeManager {
    void addIntAttribute(const std::string &name) {
        addAttribute(name, AttributeFactory::createAttribute(name, Config(BasicType::INT32)));
    }
    void addReferenceAttribute(const std::string &name) {
        addAttribute(name, std::make_shared<ReferenceAttribute>(name));
    }
    const ReferenceAttribute *getReferenceAttribute(const std::string &name) const {
        AttributeGuard::UP guard = getAttribute(name);
        auto *result = dynamic_cast<const ReferenceAttribute *>(guard->get());
        assert(result != nullptr);
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
set(const std::string &name,
    const std::string &referenceField,
    const std::string &targetField,
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
    assert(result != nullptr);
    return *result;
}

VESPA_THREAD_STACK_TAG(attribute_executor)

using AddVector = std::vector<MockGidToLidChangeHandler::AddEntry>;
using RemoveVector = std::vector<MockGidToLidChangeHandler::RemoveEntry>;

class DocumentDBReferenceResolverTest : public ::testing::Test {
protected:
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
    DocumentDBReferenceResolverTest();
    ~DocumentDBReferenceResolverTest() override;
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
    const IGidToLidMapperFactory *getMapperFactoryPtr(const std::string &attrName) {
        return attrMgr.getReferenceAttribute(attrName)->getGidToLidMapperFactory().get();
    }
    void assertImportedAttribute(const std::string &name,
                                 const std::string &referenceField,
                                 const std::string &targetField,
                                 bool useSearchCache,
                                 const ImportedAttributeVector::SP & attr) {
        ASSERT_TRUE(attr.get());
        EXPECT_EQ(name, attr->getName());
        EXPECT_EQ(attrMgr.getReferenceAttribute(referenceField), attr->getReferenceAttribute().get());
        EXPECT_EQ(parentReference->getAttribute(targetField).get(), attr->getTargetAttribute().get());
        EXPECT_EQ(useSearchCache, attr->getSearchCache().get() != nullptr);
    }

    MockGidToLidChangeHandler &getGidToLidChangeHandler(const std::string &referencedDocTypeName) {
        auto ireference = registry.get(referencedDocTypeName);
        auto reference = std::dynamic_pointer_cast<MyDocumentDBReference>(ireference);
        assert(reference);
        return reference->getGidToLidChangeHandler();
    }

    const AddVector& get_parent_adds(const std::string &referenced_doc_type_name) {
        auto& handler = getGidToLidChangeHandler(referenced_doc_type_name);
        return handler.get_adds();
    }

    const RemoveVector& get_parent_removes(const std::string &referenced_doc_type_name) {
        auto& handler = getGidToLidChangeHandler(referenced_doc_type_name);
        return handler.get_removes();
    }
};

DocumentDBReferenceResolverTest::DocumentDBReferenceResolverTest()
    : testing::Test(),
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

DocumentDBReferenceResolverTest::~DocumentDBReferenceResolverTest() = default;

TEST_F(DocumentDBReferenceResolverTest, require_that_reference_attributes_are_connected_to_gid_mapper)
{
    resolve();
    EXPECT_EQ(factory.get(), getMapperFactoryPtr("ref"));
    EXPECT_EQ(factory.get(), getMapperFactoryPtr("other_ref"));
}

TEST_F(DocumentDBReferenceResolverTest, require_that_reference_attributes_are_not_connected_to_gid_mapper_during_replay)
{
    resolveReplay();
    EXPECT_EQ(static_cast<IGidToLidMapperFactory *>(nullptr), getMapperFactoryPtr("ref"));
    EXPECT_EQ(static_cast<IGidToLidMapperFactory *>(nullptr), getMapperFactoryPtr("other_ref"));
}

TEST_F(DocumentDBReferenceResolverTest, require_that_imported_attributes_are_instantiated_without_search_cache_as_default)
{
    auto repo = resolve();
    EXPECT_EQ(2u, repo->size());
    assertImportedAttribute("imported_a", "ref", "target_a", false, repo->get("imported_a"));
    assertImportedAttribute("imported_b", "other_ref", "target_b", false, repo->get("imported_b"));
}

TEST_F(DocumentDBReferenceResolverTest, require_that_imported_attributes_are_instantiated_with_search_cache_if_visibility_delay_gt_0)
{
    auto repo = resolve(1s);
    EXPECT_EQ(2u, repo->size());
    assertImportedAttribute("imported_a", "ref", "target_a", true, repo->get("imported_a"));
    assertImportedAttribute("imported_b", "other_ref", "target_b", true, repo->get("imported_b"));
}

TEST_F(DocumentDBReferenceResolverTest, require_that_missing_target_attribute_prevents_creation_of_imported_attribute)
{
    parentReference->removeAttribute("target_a");
    auto repo =  resolve();
    EXPECT_EQ(1u, repo->size());
    EXPECT_FALSE(repo->get("imported_a"));
    EXPECT_TRUE(repo->get("imported_b"));
}

TEST_F(DocumentDBReferenceResolverTest, require_that_listeners_are_added)
{
    resolve();
    EXPECT_EQ((AddVector{{"child","other_ref"},{"child","ref"}}), get_parent_adds("parent"));
    EXPECT_EQ((RemoveVector{{"child", {"other_ref","ref"}}}), get_parent_removes("parent"));
    auto &listeners = getGidToLidChangeHandler("parent").getListeners();
    EXPECT_EQ(2u, listeners.size());
    EXPECT_EQ(attrMgr.getReferenceAttribute("other_ref"), getReferenceAttribute(*listeners[0]));
    EXPECT_EQ(attrMgr.getReferenceAttribute("ref"), getReferenceAttribute(*listeners[1]));
    EXPECT_EQ(AddVector{}, get_parent_adds("parent2"));
    EXPECT_EQ((RemoveVector{{"child", {}}}), get_parent_removes("parent2"));
}

TEST_F(DocumentDBReferenceResolverTest, require_that_listeners_are_removed)
{
    teardown();
    EXPECT_EQ(AddVector{}, get_parent_adds("parent"));
    EXPECT_EQ((RemoveVector{{"child", {}}}), get_parent_removes("parent"));
    EXPECT_EQ(AddVector{}, get_parent_adds("parent2"));
    EXPECT_EQ(RemoveVector{}, get_parent_removes("parent2"));
}

GTEST_MAIN_RUN_ALL_TESTS()
