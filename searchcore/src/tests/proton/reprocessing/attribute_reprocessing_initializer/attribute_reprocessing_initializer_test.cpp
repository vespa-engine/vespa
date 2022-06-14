// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/attribute/attribute_directory.h>
#include <vespa/searchcore/proton/attribute/attribute_populator.h>
#include <vespa/searchcore/proton/attribute/attributedisklayout.h>
#include <vespa/searchcore/proton/attribute/attributemanager.h>
#include <vespa/searchcore/proton/attribute/document_field_populator.h>
#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/searchcore/proton/common/i_indexschema_inspector.h>
#include <vespa/searchcore/proton/reprocessing/attribute_reprocessing_initializer.h>
#include <vespa/searchcore/proton/reprocessing/i_reprocessing_handler.h>
#include <vespa/searchcore/proton/test/attribute_utils.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/interlock.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/test/directory_handler.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/vespalib/util/foreground_thread_executor.h>
#include <vespa/vespalib/util/foregroundtaskexecutor.h>

#include <vespa/log/log.h>
LOG_SETUP("attribute_reprocessing_initializer_test");

using namespace proton;
using namespace search;
using namespace search::index;

using proton::test::AttributeUtils;
using search::attribute::BasicType;
using search::attribute::Config;
using search::index::schema::DataType;
using search::test::DirectoryHandler;
using vespalib::ForegroundTaskExecutor;
using vespalib::ForegroundThreadExecutor;

const vespalib::string TEST_DIR = "test_output";
const SerialNum INIT_SERIAL_NUM = 10;
typedef std::vector<vespalib::string> StringVector;
typedef std::set<vespalib::string> StringSet;
typedef AttributeReprocessingInitializer::Config ARIConfig;

struct MyReprocessingHandler : public IReprocessingHandler
{
    IReprocessingReader::SP _reader;
    std::vector<IReprocessingRewriter::SP> _rewriters;
    MyReprocessingHandler() : _reader(), _rewriters() {}
    void addReader(const IReprocessingReader::SP &reader) override {
        _reader = reader;
    }
    void addRewriter(const IReprocessingRewriter::SP &rewriter) override {
        _rewriters.push_back(rewriter);
    }
};

struct MyConfig
{
    DummyFileHeaderContext _fileHeaderContext;
    ForegroundTaskExecutor _attributeFieldWriter;
    ForegroundThreadExecutor _shared;
    HwInfo _hwInfo;
    AttributeManager::SP _mgr;
    search::index::Schema _schema;
    std::set<vespalib::string> _fields;
    MyConfig();
    ~MyConfig();
    void addFields(const StringVector &fields) {
        for (auto field : fields) {
            _fields.insert(field);
        }
    }
    void addAttrs(const StringVector &attrs) {
        for (auto attr : attrs) {
            if (attr == "tensor") {
                _mgr->addAttribute({attr, AttributeUtils::getTensorConfig()}, 1);
                _schema.addAttributeField(Schema::AttributeField(attr, DataType::TENSOR));
            } else if (attr == "predicate") {
                _mgr->addAttribute({attr, AttributeUtils::getPredicateConfig()}, 1);
                _schema.addAttributeField(Schema::AttributeField(attr, DataType::BOOLEANTREE));
            } else {
                _mgr->addAttribute({attr, AttributeUtils::getStringConfig()}, 1);
                _schema.addAttributeField(Schema::AttributeField(attr, DataType::STRING));
            }
        }
    }
    void addIndexField(const vespalib::string &name) {
        _schema.addIndexField(Schema::IndexField(name, DataType::STRING));
    }
};

MyConfig::MyConfig()
    : _fileHeaderContext(),
      _attributeFieldWriter(),
      _shared(),
      _hwInfo(),
      _mgr(new AttributeManager(TEST_DIR, "test.subdb", TuneFileAttributes(),
                                _fileHeaderContext, std::make_shared<search::attribute::Interlock>(),
                                _attributeFieldWriter, _shared, _hwInfo)),
      _schema()
{}
MyConfig::~MyConfig() = default;

struct MyDocTypeInspector : public IDocumentTypeInspector
{
    const MyConfig &_oldCfg;
    const MyConfig &_newCfg;
    MyDocTypeInspector(const MyConfig &oldCfg, const MyConfig &newCfg)
        : _oldCfg(oldCfg),
          _newCfg(newCfg)
    {
    }
    virtual bool hasUnchangedField(const vespalib::string &name) const override {
        return _oldCfg._fields.count(name) > 0 &&
            _newCfg._fields.count(name) > 0;
    }
};

struct MyIndexschemaInspector : public IIndexschemaInspector
{
    const search::index::Schema &_schema;
    MyIndexschemaInspector(const search::index::Schema &schema)
        : _schema(schema)
    {
    }
    virtual bool isStringIndex(const vespalib::string &name) const override {
        uint32_t fieldId = _schema.getIndexFieldId(name);
        if (fieldId == Schema::UNKNOWN_FIELD_ID) {
            return false;
        }
        const auto &field = _schema.getIndexField(fieldId);
        return (field.getDataType() == DataType::STRING);
    }
};

class InitializerTest : public ::testing::Test {
public:
    DirectoryHandler _dirHandler;
    DummyFileHeaderContext _fileHeaderContext;
    ForegroundTaskExecutor _attributeFieldWriter;
    ForegroundThreadExecutor _shared;
    HwInfo _hwInfo;
    AttributeManager::SP _mgr;
    MyConfig _oldCfg;
    MyConfig _newCfg;
    MyDocTypeInspector _inspector;
    AttributeReprocessingInitializer::UP _initializer;
    MyReprocessingHandler _handler;
    InitializerTest()
        : _dirHandler(TEST_DIR),
          _fileHeaderContext(),
          _attributeFieldWriter(),
          _shared(),
          _hwInfo(),
          _mgr(new AttributeManager(TEST_DIR, "test.subdb", TuneFileAttributes(),
                                    _fileHeaderContext, std::make_shared<search::attribute::Interlock>(),
                                    _attributeFieldWriter, _shared, _hwInfo)),
          _oldCfg(),
          _newCfg(),
          _inspector(_oldCfg, _newCfg),
          _initializer(),
          _handler()
    {
    }
    ~InitializerTest() { }
    void init() {
        MyIndexschemaInspector oldIndexschemaInspector(_oldCfg._schema);
        _initializer.reset(new AttributeReprocessingInitializer
                           (ARIConfig(_newCfg._mgr, _newCfg._schema),
                            ARIConfig(_oldCfg._mgr, _oldCfg._schema),
                            _inspector, oldIndexschemaInspector,
                            "test", INIT_SERIAL_NUM));
        _initializer->initialize(_handler);
    }
    InitializerTest &addOldConfig(const StringVector &fields, const StringVector &attrs) {
        return addConfig(fields, attrs, _oldCfg);
    }
    InitializerTest &addNewConfig(const StringVector &fields, const StringVector &attrs) {
        return addConfig(fields, attrs, _newCfg);
    }
    InitializerTest &addConfig(const StringVector &fields, const StringVector &attrs, MyConfig &cfg) {
        cfg.addFields(fields);
        cfg.addAttrs(attrs);
        return *this;
    }
    void assertAttributes(const StringSet &expAttrs) {
        if (expAttrs.empty()) {
            EXPECT_TRUE(_handler._reader.get() == nullptr);;
        } else {
            const auto & populator = dynamic_cast<const AttributePopulator &>(*_handler._reader);
            std::vector<search::AttributeVector *> attrList = populator.getWriter().getWritableAttributes();
            std::set<vespalib::string> actAttrs;
            for (const auto attr : attrList) {
                actAttrs.insert(attr->getName());
            }
            EXPECT_EQ(expAttrs, actAttrs);
        }
    }
    void assertFields(const StringSet &expFields) {
        if (expFields.empty()) {
            EXPECT_EQ(0u, _handler._rewriters.size());
        } else {
            StringSet actFields;
            for (auto rewriter : _handler._rewriters) {
                const auto & populator = dynamic_cast<const DocumentFieldPopulator &>(*rewriter);
                actFields.insert(populator.getAttribute().getName());
            }
            EXPECT_EQ(expFields, actFields);
        }
    }
};

class Fixture : public InitializerTest {
    virtual void TestBody() override {}
};

TEST_F(InitializerTest, require_that_new_field_does_NOT_require_attribute_populate)
{
    addOldConfig({}, {}).addNewConfig({"a"}, {"a"}).init();
    assertAttributes({});
}

TEST_F(InitializerTest, require_that_added_attribute_aspect_does_require_attribute_populate)
{
    addOldConfig({"a"}, {}).addNewConfig({"a"}, {"a"}).init();
    assertAttributes({"a"});
}

TEST_F(InitializerTest, require_that_initializer_can_setup_populate_of_several_attributes)
{
    addOldConfig({"a", "b", "c", "d"}, {"a", "b"}).
            addNewConfig({"a", "b", "c", "d"}, {"a", "b", "c", "d"}).init();
    assertAttributes({"c", "d"});
}

TEST_F(InitializerTest, require_that_new_field_does_NOT_require_document_field_populate)
{
    addOldConfig({}, {}).addNewConfig({"a"}, {"a"}).init();
    assertFields({});
}

TEST_F(InitializerTest, require_that_removed_field_does_NOT_require_document_field_populate)
{
    addOldConfig({"a"}, {"a"}).addNewConfig({}, {}).init();
    assertFields({});
}

TEST_F(InitializerTest, require_that_removed_attribute_aspect_does_require_document_field_populate)
{
    addOldConfig({"a"}, {"a"}).addNewConfig({"a"}, {}).init();
    assertFields({"a"});
}

TEST_F(InitializerTest, require_that_removed_attribute_aspect_when_also_index_field_does_NOT_require_document_field_populate)
{
    addOldConfig({"a"}, {"a"}).addNewConfig({"a"}, {});
    _oldCfg.addIndexField("a");
    _newCfg.addIndexField("a");
    init();
    assertFields({});
}

TEST_F(InitializerTest, require_that_initializer_can_setup_populate_of_several_document_fields)
{
    addOldConfig({"a", "b", "c", "d"}, {"a", "b", "c", "d"}).
            addNewConfig({"a", "b", "c", "d"}, {"a", "b"}).init();
    assertFields({"c", "d"});
}

TEST_F(InitializerTest, require_that_initializer_can_setup_both_attribute_and_document_field_populate)
{
    addOldConfig({"a", "b"}, {"a"}).
            addNewConfig({"a", "b"}, {"b"}).init();
    assertAttributes({"b"});
    assertFields({"a"});
}

TEST_F(InitializerTest, require_that_adding_attribute_aspect_on_tensor_field_require_attribute_populate)
{
    addOldConfig({"tensor"}, {}).
            addNewConfig({"tensor"}, {"tensor"}).init();
    assertAttributes({"tensor"});
    assertFields({});
}

TEST_F(InitializerTest, require_that_removing_attribute_aspect_from_tensor_field_require_document_field_populate)
{
    addOldConfig({"tensor"}, {"tensor"}).
            addNewConfig({"tensor"}, {}).init();
    assertAttributes({});
    assertFields({"tensor"});
}

TEST_F(InitializerTest, require_that_predicate_fields_are_not_populated_from_attribute)
{
    addOldConfig({"a", "b", "c", "d", "predicate"}, {"a", "b", "c", "d", "predicate"}).
            addNewConfig({"a", "b", "c", "d", "predicate"}, {"a", "b"}).init();
    assertFields({"c", "d"});
}

TEST(InterruptedTest, require_that_added_attribute_aspect_with_flushed_attribute_after_interruptted_reprocessing_does_not_require_attribute_populate)
{
    {
        auto diskLayout = AttributeDiskLayout::create(TEST_DIR);
        auto dir = diskLayout->createAttributeDir("a");
        auto writer = dir->getWriter();
        writer->createInvalidSnapshot(INIT_SERIAL_NUM);
        auto snapshotdir = writer->getSnapshotDir(INIT_SERIAL_NUM);
        std::filesystem::create_directory(std::filesystem::path(snapshotdir));
        writer->markValidSnapshot(INIT_SERIAL_NUM);
        auto av = AttributeFactory::createAttribute(snapshotdir + "/a",
                                                    Config(BasicType::STRING));
        av->save();
    }
    Fixture f;
    f.addOldConfig({"a"}, {}).addNewConfig({"a"}, {"a"}).init();
    f.assertAttributes({});
}

TEST_F(InitializerTest, require_that_removed_attribute_aspect_from_struct_field_does_not_require_document_field_populate)
{
    addOldConfig({"array.a"}, {"array.a"}).addNewConfig({"array.a"}, {}).init();
    assertFields({});
}

TEST_F(InitializerTest, require_that_added_attribute_aspect_to_struct_field_requires_attribute_populate)
{
    addOldConfig({"array.a"}, {}).addNewConfig({"array.a"}, {"array.a"}).init();
    assertAttributes({"array.a"});
}

GTEST_MAIN_RUN_ALL_TESTS()
