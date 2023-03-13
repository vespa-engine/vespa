// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/attribute/attribute_collection_spec_factory.h>
#include <vespa/searchcore/proton/attribute/attribute_writer.h>
#include <vespa/searchcore/proton/attribute/attributemanager.h>
#include <vespa/searchcore/proton/attribute/filter_attribute_manager.h>
#include <vespa/searchcore/proton/attribute/ifieldupdatecallback.h>
#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>
#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/searchcore/proton/test/attribute_utils.h>
#include <vespa/searchcore/proton/test/mock_attribute_manager.h>
#include <vespa/searchcorespi/flush/iflushtarget.h>
#include <vespa/searchlib/attribute/attribute_read_guard.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/bitvector_search_cache.h>
#include <vespa/searchlib/attribute/imported_attribute_vector.h>
#include <vespa/searchlib/attribute/imported_attribute_vector_factory.h>
#include <vespa/searchlib/attribute/interlock.h>
#include <vespa/searchlib/attribute/predicate_attribute.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/predicate/predicate_hash.h>
#include <vespa/searchlib/predicate/predicate_index.h>
#include <vespa/searchlib/tensor/dense_tensor_attribute.h>
#include <vespa/searchlib/tensor/tensor_attribute.h>
#include <vespa/searchlib/test/directory_handler.h>
#include <vespa/searchlib/test/doc_builder.h>
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchcommon/attribute/iattributevector.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/config-attributes.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/datatype/mapdatatype.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/mapfieldvalue.h>
#include <vespa/document/fieldvalue/predicatefieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/document/predicate/predicate_slime_builder.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/update/arithmeticvalueupdate.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/test/value_compare.h>
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/foreground_thread_executor.h>
#include <vespa/vespalib/util/foregroundtaskexecutor.h>
#include <vespa/vespalib/util/gate.h>
#include <vespa/vespalib/util/idestructorcallback.h>
#include <vespa/vespalib/util/sequencedtaskexecutorobserver.h>

#include <vespa/log/log.h>
LOG_SETUP("attribute_test");

namespace vespa { namespace config { namespace search {}}}

using namespace config;
using namespace document;
using namespace proton;
using namespace search::index;
using namespace search;
using namespace vespa::config::search;

using proton::ImportedAttributesRepo;
using proton::test::AttributeUtils;
using proton::test::MockAttributeManager;
using search::TuneFileAttributes;
using search::attribute::BitVectorSearchCache;
using search::attribute::DistanceMetric;
using search::attribute::HnswIndexParams;
using search::attribute::IAttributeVector;
using search::attribute::ImportedAttributeVector;
using search::attribute::ImportedAttributeVectorFactory;
using search::attribute::ReferenceAttribute;
using search::index::DummyFileHeaderContext;
using search::predicate::PredicateHash;
using search::predicate::PredicateIndex;
using search::tensor::DenseTensorAttribute;
using search::tensor::PrepareResult;
using search::tensor::TensorAttribute;
using search::test::DirectoryHandler;
using search::test::DocBuilder;
using std::string;
using vespalib::ForegroundTaskExecutor;
using vespalib::ForegroundThreadExecutor;
using vespalib::SequencedTaskExecutorObserver;
using vespalib::datastore::CompactionStrategy;
using vespalib::eval::SimpleValue;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;
using vespalib::eval::ValueType;
using vespalib::GateCallback;
using vespalib::IDestructorCallback;

using AVBasicType = search::attribute::BasicType;
using AVCollectionType = search::attribute::CollectionType;
using AVConfig = search::attribute::Config;
using LidVector = LidVectorContext::LidVector;

namespace {

constexpr uint64_t createSerialNum = 42u;

}

AVConfig
unregister(const AVConfig & cfg)
{
    AVConfig retval = cfg;
    return retval;
}

const string test_dir = "test_output";
const AVConfig INT32_SINGLE = unregister(AVConfig(AVBasicType::INT32));

void
fillAttribute(const AttributeVector::SP &attr, uint32_t numDocs, int64_t value, uint64_t lastSyncToken)
{
    AttributeUtils::fillAttribute(*attr, numDocs, value, lastSyncToken);
}

void
fillAttribute(const AttributeVector::SP &attr, uint32_t from, uint32_t to, int64_t value, uint64_t lastSyncToken)
{
    AttributeUtils::fillAttribute(*attr, from, to, value, lastSyncToken);
}

const std::shared_ptr<IDestructorCallback> emptyCallback;

class AttributeWriterTest : public ::testing::Test {
public:
    DirectoryHandler _dirHandler;
    std::unique_ptr<ForegroundTaskExecutor> _attributeFieldWriterReal;
    std::unique_ptr<SequencedTaskExecutorObserver> _attributeFieldWriter;
    ForegroundThreadExecutor _shared;
    std::shared_ptr<MockAttributeManager> _mgr;
    std::unique_ptr<AttributeWriter> _aw;

    AttributeWriterTest()
        : _dirHandler(test_dir),
          _attributeFieldWriterReal(),
          _attributeFieldWriter(),
          _shared(),
          _mgr(),
          _aw()
    {
        setup(1);
    }
    ~AttributeWriterTest() override;
    void setup(uint32_t threads) {
        _aw.reset();
        _attributeFieldWriterReal = std::make_unique<ForegroundTaskExecutor>(threads);
        _attributeFieldWriter = std::make_unique<SequencedTaskExecutorObserver>(*_attributeFieldWriterReal);
        _mgr = std::make_shared<MockAttributeManager>();
        _mgr->set_writer(*_attributeFieldWriter);
        _mgr->set_shared_executor(_shared);
        allocAttributeWriter();
    }
    void allocAttributeWriter() {
        _aw = std::make_unique<AttributeWriter>(_mgr);
    }
    AttributeVector::SP addAttribute(const vespalib::string &name) {
        return addAttribute({name, AVConfig(AVBasicType::INT32)});
    }
    AttributeVector::SP addAttribute(const AttributeSpec &spec) {
        auto ret = _mgr->addAttribute(spec.getName(),
                                      AttributeFactory::createAttribute(spec.getName(), spec.getConfig()));
        return ret;
    }
    void add_attribute(AttributeVector::SP attr) {
        _mgr->addAttribute(attr->getName(), std::move(attr));
    }
    void put(SerialNum serialNum, const Document &doc, DocumentIdT lid) {
        _aw->put(serialNum, doc, lid, emptyCallback);
        commit(serialNum);
    }
    void update(SerialNum serialNum, const DocumentUpdate &upd,
                DocumentIdT lid, IFieldUpdateCallback & onUpdate) {
        _aw->update(serialNum, upd, lid, emptyCallback, onUpdate);
        commit(serialNum);
    }
    void update(SerialNum serialNum, const Document &doc, DocumentIdT lid) {
        _aw->update(serialNum, doc, lid, emptyCallback);
        commit(serialNum);
    }
    void remove(SerialNum serialNum, DocumentIdT lid) {
        _aw->remove(serialNum, lid, emptyCallback);
        commit(serialNum);
    }
    void remove(const LidVector &lidVector, SerialNum serialNum) {
        _aw->remove(lidVector, serialNum, emptyCallback);
        commit(serialNum);
    }
    void commit(SerialNum serialNum) {
        _aw->forceCommit(serialNum, emptyCallback);
    }
    void assertExecuteHistory(std::vector<uint32_t> expExecuteHistory) {
        auto includeCommit = expExecuteHistory;
        includeCommit.insert(includeCommit.end(), expExecuteHistory.begin(), expExecuteHistory.end());
        EXPECT_EQ(includeCommit, _attributeFieldWriter->getExecuteHistory());
    }
    SerialNum test_force_commit(AttributeVector &attr, SerialNum serialNum) {
        vespalib::Gate gate;
        _aw->forceCommit(serialNum, std::make_shared<GateCallback>(gate));
        gate.await();
        return attr.getStatus().getLastSyncToken();
    }
};

AttributeWriterTest::~AttributeWriterTest() = default;

TEST_F(AttributeWriterTest, handles_put)
{
    DocBuilder db([](auto& header)
                  { using namespace document::config_builder;
                      header.addField("a1", DataType::T_INT)
                          .addField("a2", Array(DataType::T_INT))
                          .addField("a3", DataType::T_FLOAT)
                          .addField("a4", DataType::T_STRING); });
    auto a1 = addAttribute("a1");
    auto a2 = addAttribute({"a2", AVConfig(AVBasicType::INT32, AVCollectionType::ARRAY)});
    auto a3 = addAttribute({"a3", AVConfig(AVBasicType::FLOAT)});
    auto a4 = addAttribute({"a4", AVConfig(AVBasicType::STRING)});
    allocAttributeWriter();

    attribute::IntegerContent ibuf;
    attribute::FloatContent fbuf;
    attribute::ConstCharContent sbuf;
    { // empty document should give default values
        EXPECT_EQ(1u, a1->getNumDocs());
        put(1, *db.make_document("id:ns:searchdocument::1"), 1);
        EXPECT_EQ(2u, a1->getNumDocs());
        EXPECT_EQ(2u, a2->getNumDocs());
        EXPECT_EQ(2u, a3->getNumDocs());
        EXPECT_EQ(2u, a4->getNumDocs());
        EXPECT_EQ(1u, a1->getStatus().getLastSyncToken());
        EXPECT_EQ(1u, a2->getStatus().getLastSyncToken());
        EXPECT_EQ(1u, a3->getStatus().getLastSyncToken());
        EXPECT_EQ(1u, a4->getStatus().getLastSyncToken());
        ibuf.fill(*a1, 1);
        EXPECT_EQ(1u, ibuf.size());
        EXPECT_TRUE(search::attribute::isUndefined<int32_t>(ibuf[0]));
        ibuf.fill(*a2, 1);
        EXPECT_EQ(0u, ibuf.size());
        fbuf.fill(*a3, 1);
        EXPECT_EQ(1u, fbuf.size());
        EXPECT_TRUE(search::attribute::isUndefined<float>(fbuf[0]));
        sbuf.fill(*a4, 1);
        EXPECT_EQ(1u, sbuf.size());
        EXPECT_EQ(strcmp("", sbuf[0]), 0);
    }
    { // document with single value & multi value attribute
        auto doc = db.make_document("id:ns:searchdocument::2");
        doc->setValue("a1", IntFieldValue(10));
        auto int_array = db.make_array("a2");
        int_array.add(IntFieldValue(20));
        int_array.add(IntFieldValue(30));
        doc->setValue("a2",int_array);
        put(2, *doc, 2);
        EXPECT_EQ(3u, a1->getNumDocs());
        EXPECT_EQ(3u, a2->getNumDocs());
        EXPECT_EQ(2u, a1->getStatus().getLastSyncToken());
        EXPECT_EQ(2u, a2->getStatus().getLastSyncToken());
        EXPECT_EQ(2u, a3->getStatus().getLastSyncToken());
        EXPECT_EQ(2u, a4->getStatus().getLastSyncToken());
        ibuf.fill(*a1, 2);
        EXPECT_EQ(1u, ibuf.size());
        EXPECT_EQ(10u, ibuf[0]);
        ibuf.fill(*a2, 2);
        EXPECT_EQ(2u, ibuf.size());
        EXPECT_EQ(20u, ibuf[0]);
        EXPECT_EQ(30u, ibuf[1]);
    }
    { // replace existing document
        auto doc = db.make_document("id:ns:searchdocument::2");
        doc->setValue("a1", IntFieldValue(100));
        auto int_array = db.make_array("a2");
        int_array.add(IntFieldValue(200));
        int_array.add(IntFieldValue(300));
        int_array.add(IntFieldValue(400));
        doc->setValue("a2",int_array);
        put(3, *doc, 2);
        EXPECT_EQ(3u, a1->getNumDocs());
        EXPECT_EQ(3u, a2->getNumDocs());
        EXPECT_EQ(3u, a1->getStatus().getLastSyncToken());
        EXPECT_EQ(3u, a2->getStatus().getLastSyncToken());
        EXPECT_EQ(3u, a3->getStatus().getLastSyncToken());
        EXPECT_EQ(3u, a4->getStatus().getLastSyncToken());
        ibuf.fill(*a1, 2);
        EXPECT_EQ(1u, ibuf.size());
        EXPECT_EQ(100u, ibuf[0]);
        ibuf.fill(*a2, 2);
        EXPECT_EQ(3u, ibuf.size());
        EXPECT_EQ(200u, ibuf[0]);
        EXPECT_EQ(300u, ibuf[1]);
        EXPECT_EQ(400u, ibuf[2]);
    }
}

TEST_F(AttributeWriterTest, handles_predicate_put)
{
    DocBuilder db([](auto& header) { header.addField("a1", DataType::T_PREDICATE); });
    auto a1 = addAttribute({"a1", AVConfig(AVBasicType::PREDICATE)});
    allocAttributeWriter();

    PredicateIndex &index = static_cast<PredicateAttribute &>(*a1).getIndex();

    // empty document should give default values
    EXPECT_EQ(1u, a1->getNumDocs());
    put(1, *db.make_document("id:ns:searchdocument::1"), 1);
    EXPECT_EQ(2u, a1->getNumDocs());
    EXPECT_EQ(1u, a1->getStatus().getLastSyncToken());
    EXPECT_EQ(0u, index.getZeroConstraintDocs().size());

    // document with single value attribute
    PredicateSlimeBuilder builder;
    auto doc = db.make_document("id:ns:searchdocument::2");
    doc->setValue("a1", PredicateFieldValue(builder.true_predicate().build()));
    put(2, *doc, 2);
    EXPECT_EQ(3u, a1->getNumDocs());
    EXPECT_EQ(2u, a1->getStatus().getLastSyncToken());
    EXPECT_EQ(1u, index.getZeroConstraintDocs().size());

    auto it = index.getIntervalIndex().lookup(PredicateHash::hash64("foo=bar"));
    EXPECT_FALSE(it.valid());

    // replace existing document
    doc = db.make_document("id:ns:searchdocument::2");
    doc->setValue("a1", PredicateFieldValue(builder.feature("foo").value("bar").build()));
    put(3, *doc, 2);
    EXPECT_EQ(3u, a1->getNumDocs());
    EXPECT_EQ(3u, a1->getStatus().getLastSyncToken());

    it = index.getIntervalIndex().lookup(PredicateHash::hash64("foo=bar"));
    EXPECT_TRUE(it.valid());
}

void
assertUndefined(const IAttributeVector &attr, uint32_t docId)
{
    EXPECT_TRUE(search::attribute::isUndefined<int32_t>(attr.getInt(docId)));
}

TEST_F(AttributeWriterTest, handles_remove)
{
    auto a1 = addAttribute("a1");
    auto a2 = addAttribute("a2");
    constexpr SerialNum fill_serial_num = 2;
    allocAttributeWriter();
    fillAttribute(a1, 1, 10, fill_serial_num);
    fillAttribute(a2, 1, 20, fill_serial_num);
    remove(fill_serial_num - 1, 1); // lower sync token than during fill => ignored
    remove(fill_serial_num, 1); // same sync token as during fill  => ignored
    EXPECT_EQ(10, a1->getInt(1));
    EXPECT_EQ(20, a2->getInt(1));
    remove(fill_serial_num + 1, 1); // newer sync token => not ignored
    assertUndefined(*a1, 1);
    assertUndefined(*a2, 1);
}

TEST_F(AttributeWriterTest, handles_batch_remove)
{
    auto a1 = addAttribute("a1");
    auto a2 = addAttribute("a2");
    allocAttributeWriter();
    fillAttribute(a1, 4, 22, 1);
    fillAttribute(a2, 4, 33, 1);

    LidVector lidsToRemove = {1,3};
    remove(lidsToRemove, 2);

    assertUndefined(*a1, 1);
    EXPECT_EQ(22, a1->getInt(2));
    assertUndefined(*a1, 3);
    assertUndefined(*a2, 1);
    EXPECT_EQ(33, a2->getInt(2));
    assertUndefined(*a2, 3);
}

void
verifyAttributeContent(const AttributeVector & v, uint32_t lid, vespalib::stringref expected)
{
    attribute::ConstCharContent sbuf;
    sbuf.fill(v, lid);
    EXPECT_EQ(1u, sbuf.size());
    EXPECT_EQ(expected, sbuf[0]);
}

TEST_F(AttributeWriterTest, visibility_delay_is_honoured)
{
    auto a1 = addAttribute({"a1", AVConfig(AVBasicType::STRING)});
    allocAttributeWriter();

    DocBuilder db([](auto& header) { header.addField("a1", DataType::T_STRING); });
    EXPECT_EQ(1u, a1->getNumDocs());
    EXPECT_EQ(0u, a1->getStatus().getLastSyncToken());
    auto doc = db.make_document("id:ns:searchdocument::1");
    doc->setValue("a1", StringFieldValue("10"));
    put(3, *doc, 1);
    EXPECT_EQ(2u, a1->getNumDocs());
    EXPECT_EQ(3u, a1->getStatus().getLastSyncToken());
    AttributeWriter awDelayed(_mgr);
    awDelayed.put(4, *doc, 2, emptyCallback);
    EXPECT_EQ(3u, a1->getNumDocs());
    EXPECT_EQ(3u, a1->getStatus().getLastSyncToken());
    awDelayed.put(5, *doc, 4, emptyCallback);
    EXPECT_EQ(5u, a1->getNumDocs());
    EXPECT_EQ(3u, a1->getStatus().getLastSyncToken());
    awDelayed.forceCommit(6, emptyCallback);
    EXPECT_EQ(6u, a1->getStatus().getLastSyncToken());

    AttributeWriter awDelayedShort(_mgr);
    awDelayedShort.put(7, *doc, 2, emptyCallback);
    EXPECT_EQ(6u, a1->getStatus().getLastSyncToken());
    awDelayedShort.put(8, *doc, 2, emptyCallback);
    awDelayedShort.forceCommit(8, emptyCallback);
    EXPECT_EQ(8u, a1->getStatus().getLastSyncToken());

    verifyAttributeContent(*a1, 2, "10");
    doc = db.make_document("id:ns:searchdocument::1");
    doc->setValue("a1", StringFieldValue("11"));
    awDelayed.put(9, *doc, 2, emptyCallback);
    doc = db.make_document("id:ns:searchdocument::1");
    doc->setValue("a1", StringFieldValue("20"));
    awDelayed.put(10, *doc, 2, emptyCallback);
    doc = db.make_document("id:ns:searchdocument::1");
    doc->setValue("a1", StringFieldValue("30"));
    awDelayed.put(11, *doc, 2, emptyCallback);
    EXPECT_EQ(8u, a1->getStatus().getLastSyncToken());
    verifyAttributeContent(*a1, 2, "10");
    awDelayed.forceCommit(12, emptyCallback);
    EXPECT_EQ(12u, a1->getStatus().getLastSyncToken());
    verifyAttributeContent(*a1, 2, "30");
}

TEST_F(AttributeWriterTest, handles_predicate_remove)
{
    auto a1 = addAttribute({"a1", AVConfig(AVBasicType::PREDICATE)});
    allocAttributeWriter();

    DocBuilder db([](auto& header) { header.addField("a1", DataType::T_PREDICATE); });

    PredicateSlimeBuilder builder;
    auto doc = db.make_document("id:ns:searchdocument::1");
    doc->setValue("a1", PredicateFieldValue(builder.true_predicate().build()));
    put(1, *doc, 1);
    EXPECT_EQ(2u, a1->getNumDocs());

    PredicateIndex &index = static_cast<PredicateAttribute &>(*a1).getIndex();
    EXPECT_EQ(1u, index.getZeroConstraintDocs().size());
    remove(2, 1);
    EXPECT_EQ(0u, index.getZeroConstraintDocs().size());
}

TEST_F(AttributeWriterTest, handles_update)
{
    auto a1 = addAttribute("a1");
    auto a2 = addAttribute("a2");
    allocAttributeWriter();

    fillAttribute(a1, 1, 10, 1);
    fillAttribute(a2, 1, 20, 1);

    DocBuilder db([](auto& header)
                            { header.addField("a1", DataType::T_INT)
                                    .addField("a2", DataType::T_INT); });
    DocumentUpdate upd(db.get_repo(), db.get_document_type(), DocumentId("id:ns:searchdocument::1"));
    upd.addUpdate(FieldUpdate(upd.getType().getField("a1"))
                  .addUpdate(std::make_unique<ArithmeticValueUpdate>(ArithmeticValueUpdate::Add, 5)));
    upd.addUpdate(FieldUpdate(upd.getType().getField("a2"))
                  .addUpdate(std::make_unique<ArithmeticValueUpdate>(ArithmeticValueUpdate::Add, 10)));

    DummyFieldUpdateCallback onUpdate;
    update(2, upd, 1, onUpdate);

    attribute::IntegerContent ibuf;
    ibuf.fill(*a1, 1);
    EXPECT_EQ(1u, ibuf.size());
    EXPECT_EQ(15u, ibuf[0]);
    ibuf.fill(*a2, 1);
    EXPECT_EQ(1u, ibuf.size());
    EXPECT_EQ(30u, ibuf[0]);

    update(2, upd, 1, onUpdate); // same sync token as previous
    try {
        update(1, upd, 1, onUpdate); // lower sync token than previous
        EXPECT_TRUE(true);  // update is ignored
    } catch (vespalib::IllegalStateException & e) {
        LOG(info, "Got expected exception: '%s'", e.getMessage().c_str());
        EXPECT_TRUE(true);
    }
}

TEST_F(AttributeWriterTest, handles_predicate_update)
{
    auto a1 = addAttribute({"a1", AVConfig(AVBasicType::PREDICATE)});
    allocAttributeWriter();
    DocBuilder db([](auto& header) { header.addField("a1", DataType::T_PREDICATE); });
    PredicateSlimeBuilder builder;
    auto doc = db.make_document("id:ns:searchdocument::1");
    doc->setValue("a1", PredicateFieldValue(builder.true_predicate().build()));
    put(1, *doc, 1);
    EXPECT_EQ(2u, a1->getNumDocs());

    DocumentUpdate upd(db.get_repo(), db.get_document_type(), DocumentId("id:ns:searchdocument::1"));
    upd.addUpdate(FieldUpdate(upd.getType().getField("a1"))
                  .addUpdate(std::make_unique<AssignValueUpdate>(std::make_unique<PredicateFieldValue>(builder.feature("foo").value("bar").build()))));

    PredicateIndex &index = static_cast<PredicateAttribute &>(*a1).getIndex();
    EXPECT_EQ(1u, index.getZeroConstraintDocs().size());
    EXPECT_FALSE(index.getIntervalIndex().lookup(PredicateHash::hash64("foo=bar")).valid());
    DummyFieldUpdateCallback onUpdate;
    update(2, upd, 1, onUpdate);
    EXPECT_EQ(0u, index.getZeroConstraintDocs().size());
    EXPECT_TRUE(index.getIntervalIndex().lookup(PredicateHash::hash64("foo=bar")).valid());
}

class AttributeCollectionSpecTest : public ::testing::Test {
public:
    AttributesConfigBuilder _builder;
    AttributeCollectionSpecFactory _factory;
    AttributeCollectionSpecTest(bool fastAccessOnly)
        : _builder(),
          _factory(AllocStrategy(search::GrowStrategy(), CompactionStrategy(), 100), fastAccessOnly)
    {
        addAttribute("a1", false);
        addAttribute("a2", true);
    }
    void addAttribute(const vespalib::string &name, bool fastAccess) {
        AttributesConfigBuilder::Attribute attr;
        attr.name = name;
        attr.fastaccess = fastAccess;
        _builder.attribute.push_back(attr);
    }
    std::unique_ptr<AttributeCollectionSpec> create(uint32_t docIdLimit, search::SerialNum serialNum) {
        return _factory.create(_builder, docIdLimit, serialNum);
    }
};

class NormalAttributeCollectionSpecTest : public AttributeCollectionSpecTest {
public:
    NormalAttributeCollectionSpecTest() : AttributeCollectionSpecTest(false) {}
};

struct FastAccessAttributeCollectionSpecTest : public AttributeCollectionSpecTest
{
    FastAccessAttributeCollectionSpecTest() : AttributeCollectionSpecTest(true) {}
};

TEST_F(NormalAttributeCollectionSpecTest, spec_can_be_created)
{
    auto spec = create(10, 20);
    EXPECT_EQ(2u, spec->getAttributes().size());
    EXPECT_EQ("a1", spec->getAttributes()[0].getName());
    EXPECT_EQ("a2", spec->getAttributes()[1].getName());
    EXPECT_EQ(10u, spec->getDocIdLimit());
    EXPECT_EQ(20u, spec->getCurrentSerialNum());
}

TEST_F(FastAccessAttributeCollectionSpecTest, spec_can_be_created)
{
    auto spec = create(10, 20);
    EXPECT_EQ(1u, spec->getAttributes().size());
    EXPECT_EQ("a2", spec->getAttributes()[0].getName());
    EXPECT_EQ(10u, spec->getDocIdLimit());
    EXPECT_EQ(20u, spec->getCurrentSerialNum());
}

const FilterAttributeManager::AttributeSet ACCEPTED_ATTRIBUTES = {"a2"};

class FilterAttributeManagerTest : public ::testing::Test {
public:
    DirectoryHandler             _dirHandler;
    DummyFileHeaderContext       _fileHeaderContext;
    ForegroundTaskExecutor       _attributeFieldWriter;
    ForegroundThreadExecutor     _shared;
    HwInfo                       _hwInfo;
    proton::AttributeManager::SP _baseMgr;
    FilterAttributeManager       _filterMgr;

    FilterAttributeManagerTest()
        : _dirHandler(test_dir),
          _fileHeaderContext(),
          _attributeFieldWriter(),
          _shared(),
          _hwInfo(),
          _baseMgr(new proton::AttributeManager(test_dir, "test.subdb",
                                                TuneFileAttributes(),
                                                _fileHeaderContext,
                                                std::make_shared<search::attribute::Interlock>(),
                                                _attributeFieldWriter,
                                                _shared,
                                                _hwInfo)),
          _filterMgr(ACCEPTED_ATTRIBUTES, _baseMgr)
    {
        _baseMgr->addAttribute({"a1", INT32_SINGLE}, createSerialNum);
        _baseMgr->addAttribute({"a2", INT32_SINGLE}, createSerialNum);
   }
};

TEST_F(FilterAttributeManagerTest, filter_attributes)
{
    EXPECT_TRUE(_filterMgr.getAttribute("a1").get() == nullptr);
    EXPECT_TRUE(_filterMgr.getAttribute("a2").get() != nullptr);
    std::vector<AttributeGuard> attrs;
    _filterMgr.getAttributeList(attrs);
    EXPECT_EQ(1u, attrs.size());
    EXPECT_EQ("a2", attrs[0]->getName());
    searchcorespi::IFlushTarget::List targets = _filterMgr.getFlushTargets();
    EXPECT_EQ(2u, targets.size());
    EXPECT_EQ("attribute.flush.a2", targets[0]->getName());
    EXPECT_EQ("attribute.shrink.a2", targets[1]->getName());
}

TEST_F(FilterAttributeManagerTest, returns_flushed_serial_number)
{
    _baseMgr->flushAll(100);
    EXPECT_EQ(0u, _filterMgr.getFlushedSerialNum("a1"));
    EXPECT_EQ(100u, _filterMgr.getFlushedSerialNum("a2"));
}

TEST_F(FilterAttributeManagerTest, readable_attribute_vector_filters_attributes)
{
    auto av = _filterMgr.readable_attribute_vector("a2");
    ASSERT_TRUE(av);
    EXPECT_EQ("a2", av->makeReadGuard(false)->attribute()->getName());

    av = _filterMgr.readable_attribute_vector("a1");
    EXPECT_FALSE(av);
}

namespace {

Value::UP make_tensor(const TensorSpec &spec) {
    return SimpleValue::from_spec(spec);
}

const vespalib::string sparse_tensor = "tensor(x{},y{})";

AttributeVector::SP
createTensorAttribute(AttributeWriterTest &t) {
    AVConfig cfg(AVBasicType::TENSOR);
    cfg.setTensorType(ValueType::from_spec(sparse_tensor));
    auto ret = t.addAttribute({"a1", cfg});
    return ret;
}

Document::UP
createTensorPutDoc(DocBuilder& builder, const Value &tensor) {
    auto doc = builder.make_document("id:ns:searchdocument::1");
    TensorFieldValue fv(*doc->getField("a1").getDataType().cast_tensor());
    fv = SimpleValue::from_value(tensor);
    doc->setValue("a1", fv);
    return doc;
}

}

TEST_F(AttributeWriterTest, can_write_to_tensor_attribute)
{
    auto a1 = createTensorAttribute(*this);
    allocAttributeWriter();
    DocBuilder builder([](auto& header) { header.addTensorField("a1", sparse_tensor); });
    auto tensor = make_tensor(TensorSpec(sparse_tensor)
                              .add({{"x", "4"}, {"y", "5"}}, 7));
    Document::UP doc = createTensorPutDoc(builder, *tensor);
    put(1, *doc, 1);
    EXPECT_EQ(2u, a1->getNumDocs());
    auto *tensorAttribute = dynamic_cast<TensorAttribute *>(a1.get());
    EXPECT_TRUE(tensorAttribute != nullptr);
    auto tensor2 = tensorAttribute->getTensor(1);
    EXPECT_TRUE(static_cast<bool>(tensor2));
    EXPECT_EQ(*tensor, *tensor2);
}

TEST_F(AttributeWriterTest, handles_tensor_assign_update)
{
    auto a1 = createTensorAttribute(*this);
    allocAttributeWriter();
    DocBuilder builder([](auto& header) { header.addTensorField("a1", sparse_tensor); });
    auto tensor = make_tensor(TensorSpec(sparse_tensor)
                              .add({{"x", "6"}, {"y", "7"}}, 9));
    auto doc = createTensorPutDoc(builder, *tensor);
    put(1, *doc, 1);
    EXPECT_EQ(2u, a1->getNumDocs());
    auto *tensorAttribute = dynamic_cast<TensorAttribute *>(a1.get());
    EXPECT_TRUE(tensorAttribute != nullptr);
    auto tensor2 = tensorAttribute->getTensor(1);
    EXPECT_TRUE(static_cast<bool>(tensor2));
    EXPECT_EQ(*tensor, *tensor2);

    DocumentUpdate upd(builder.get_repo(), builder.get_document_type(), DocumentId("id:ns:searchdocument::1"));
    auto new_tensor = make_tensor(TensorSpec(sparse_tensor)
                                  .add({{"x", "8"}, {"y", "9"}}, 11));
    TensorDataType xySparseTensorDataType(vespalib::eval::ValueType::from_spec(sparse_tensor));
    auto new_value = std::make_unique<TensorFieldValue>(xySparseTensorDataType);
    *new_value = SimpleValue::from_value(*new_tensor);
    upd.addUpdate(FieldUpdate(upd.getType().getField("a1"))
                  .addUpdate(std::make_unique<AssignValueUpdate>(std::move(new_value))));
    DummyFieldUpdateCallback onUpdate;
    update(2, upd, 1, onUpdate);
    EXPECT_EQ(2u, a1->getNumDocs());
    EXPECT_TRUE(tensorAttribute != nullptr);
    tensor2 = tensorAttribute->getTensor(1);
    EXPECT_TRUE(static_cast<bool>(tensor2));
    EXPECT_FALSE(*tensor == *tensor2);
    EXPECT_EQ(*new_tensor, *tensor2);
}

namespace {

void
assertPutDone(AttributeVector &attr, int32_t expVal)
{
    EXPECT_EQ(2u, attr.getNumDocs());
    EXPECT_EQ(1u, attr.getStatus().getLastSyncToken());
    attribute::IntegerContent ibuf;
    ibuf.fill(attr, 1);
    EXPECT_EQ(1u, ibuf.size());
    EXPECT_EQ(expVal, ibuf[0]);
}

void
putAttributes(AttributeWriterTest &t, std::vector<uint32_t> expExecuteHistory)
{
    // Since executor distribution depends on the unspecified hash function in vespalib,
    // decouple attribute names from their usage to allow for picking names that hash
    // more evenly for a particular implementation.
    vespalib::string a1_name = "a1";
    vespalib::string a2_name = "a2x";
    vespalib::string a3_name = "a3y";

    DocBuilder db([&](auto& header)
                  { header.addField(a1_name, DataType::T_INT)
                          .addField(a2_name, DataType::T_INT)
                          .addField(a3_name, DataType::T_INT); });

    auto a1 = t.addAttribute(a1_name);
    auto a2 = t.addAttribute(a2_name);
    auto a3 = t.addAttribute(a3_name);
    t.allocAttributeWriter();

    EXPECT_EQ(1u, a1->getNumDocs());
    EXPECT_EQ(1u, a2->getNumDocs());
    EXPECT_EQ(1u, a3->getNumDocs());
    auto doc = db.make_document("id:ns:searchdocument::1");
    doc->setValue(a1_name, IntFieldValue(10));
    doc->setValue(a2_name, IntFieldValue(15));
    doc->setValue(a3_name, IntFieldValue(20));
    t.put(1, *doc, 1);
    assertPutDone(*a1, 10);
    assertPutDone(*a2, 15);
    assertPutDone(*a3, 20);
    t.assertExecuteHistory(expExecuteHistory);
}

}

TEST_F(AttributeWriterTest, spreads_write_over_1_write_context)
{
    putAttributes(*this, {0});
}

TEST_F(AttributeWriterTest, spreads_write_over_2_write_contexts)
{
    setup(2);
    putAttributes(*this, {0, 1});
}

TEST_F(AttributeWriterTest, spreads_write_over_3_write_contexts)
{
    setup(8);
    putAttributes(*this, {4, 5, 6});
}

struct MockPrepareResult : public PrepareResult {
    uint32_t docid;
    const Value& tensor;
    MockPrepareResult(uint32_t docid_in, const Value& tensor_in) : docid(docid_in), tensor(tensor_in) {}
};

class MockDenseTensorAttribute : public DenseTensorAttribute {
public:
    mutable size_t prepare_set_tensor_cnt;
    mutable size_t complete_set_tensor_cnt;
    size_t clear_doc_cnt;
    const Value* exp_tensor;

    MockDenseTensorAttribute(vespalib::stringref name, const AVConfig& cfg)
        : DenseTensorAttribute(name, cfg),
          prepare_set_tensor_cnt(0),
          complete_set_tensor_cnt(0),
          clear_doc_cnt(0),
          exp_tensor()
    {}
    uint32_t clearDoc(DocId docid) override {
        ++clear_doc_cnt;
        return DenseTensorAttribute::clearDoc(docid);
    }
    std::unique_ptr<PrepareResult> prepare_set_tensor(uint32_t docid, const Value& tensor) const override {
        ++prepare_set_tensor_cnt;
        EXPECT_EQ(*exp_tensor, tensor);
        return std::make_unique<MockPrepareResult>(docid, tensor);
    }

    void complete_set_tensor(DocId docid, const Value& tensor, std::unique_ptr<PrepareResult> prepare_result) override {
        ++complete_set_tensor_cnt;
        assert(prepare_result);
        auto* mock_result = dynamic_cast<MockPrepareResult*>(prepare_result.get());
        assert(mock_result);
        EXPECT_EQ(docid, mock_result->docid);
        EXPECT_EQ(tensor, mock_result->tensor);
    }
};

const vespalib::string dense_tensor = "tensor(x[2])";

AVConfig
get_tensor_config(bool multi_threaded_indexing)
{
    AVConfig cfg(AVBasicType::TENSOR);
    cfg.setTensorType(ValueType::from_spec(dense_tensor));
    cfg.set_hnsw_index_params(HnswIndexParams(4, 4, DistanceMetric::Euclidean, multi_threaded_indexing));
    return cfg;
}

std::shared_ptr<MockDenseTensorAttribute>
make_mock_tensor_attribute(const vespalib::string& name, bool multi_threaded_indexing)
{
    auto cfg = get_tensor_config(multi_threaded_indexing);
    return std::make_shared<MockDenseTensorAttribute>(name, cfg);
}

TEST_F(AttributeWriterTest, tensor_attributes_using_two_phase_put_are_in_separate_write_contexts)
{
    addAttribute("a1");
    addAttribute({"t1", get_tensor_config(true)});
    addAttribute({"t2", get_tensor_config(true)});
    addAttribute({"t3", get_tensor_config(false)});
    allocAttributeWriter();

    const auto& ctx = _aw->get_write_contexts();
    EXPECT_EQ(3, ctx.size());
    EXPECT_FALSE(ctx[0].use_two_phase_put());
    EXPECT_EQ(2, ctx[0].getFields().size());

    EXPECT_TRUE(ctx[1].use_two_phase_put());
    EXPECT_EQ(1, ctx[1].getFields().size());
    EXPECT_EQ("t1", ctx[1].getFields()[0].getAttribute().getName());

    EXPECT_TRUE(ctx[2].use_two_phase_put());
    EXPECT_EQ(1, ctx[2].getFields().size());
    EXPECT_EQ("t2", ctx[2].getFields()[0].getAttribute().getName());
}

class TwoPhasePutTest : public AttributeWriterTest {
public:
    DocBuilder builder;
    vespalib::string doc_id;
    std::shared_ptr<MockDenseTensorAttribute> attr;
    std::unique_ptr<Value> tensor;

    TwoPhasePutTest()
        : AttributeWriterTest(),
          builder([&](auto& header) { header.addTensorField("a1", dense_tensor); }),
          doc_id("id:ns:searchdocument::1"),
          attr()
    {
        setup(2);
        attr = make_mock_tensor_attribute("a1", true);
        add_attribute(attr);
        AttributeManager::padAttribute(*attr, 4);
        attr->clear_doc_cnt = 0;
        tensor = make_tensor(TensorSpec(dense_tensor)
                                     .add({{"x", 0}}, 3).add({{"x", 1}}, 5));
        attr->exp_tensor = tensor.get();
        allocAttributeWriter();
    }
    void expect_tensor_attr_calls(size_t exp_prepare_cnt,
                                  size_t exp_complete_cnt,
                                  size_t exp_clear_doc_cnt = 0) {
        EXPECT_EQ(exp_prepare_cnt, attr->prepare_set_tensor_cnt);
        EXPECT_EQ(exp_complete_cnt, attr->complete_set_tensor_cnt);
        EXPECT_EQ(exp_clear_doc_cnt, attr->clear_doc_cnt);
    }
    Document::UP make_doc() {
        return createTensorPutDoc(builder, *tensor);
    }
    Document::UP make_no_field_doc() {
        return builder.make_document(doc_id);
    }
    Document::UP make_no_tensor_doc() {
        auto doc = builder.make_document(doc_id);
        TensorFieldValue fv(*doc->getField("a1").getDataType().cast_tensor());
        doc->setValue("a1", fv);
        return doc;
    }
    DocumentUpdate::UP make_assign_update() {
       auto upd = std::make_unique<DocumentUpdate>(builder.get_repo(),
                                                   builder.get_document_type(),
                                                   DocumentId(doc_id));
        TensorDataType tensor_type(vespalib::eval::ValueType::from_spec(dense_tensor));
        auto tensor_value = std::make_unique<TensorFieldValue>(tensor_type);
        *tensor_value = SimpleValue::from_value(*tensor);
        upd->addUpdate(FieldUpdate(upd->getType().getField("a1")).addUpdate(std::make_unique<AssignValueUpdate>(std::move(tensor_value))));
        return upd;
    }
    void expect_shared_executor_tasks(size_t exp_accepted_tasks) {
        auto stats = _shared.getStats();
        EXPECT_EQ(exp_accepted_tasks, stats.acceptedTasks);
        EXPECT_EQ(0, stats.rejectedTasks);
    }
};

TEST_F(TwoPhasePutTest, handles_put_in_two_phases_when_specified_for_tensor_attribute)
{
    auto doc = make_doc();

    put(1, *doc, 1);
    expect_tensor_attr_calls(1, 1);
    expect_shared_executor_tasks(1);
    assertExecuteHistory({0});

    put(2, *doc, 2);
    expect_tensor_attr_calls(2, 2);
    expect_shared_executor_tasks(2);
    assertExecuteHistory({0, 0});
}

TEST_F(TwoPhasePutTest, put_is_ignored_when_serial_number_is_older_or_equal_to_attribute)
{
    auto doc = make_doc();
    attr->commit(CommitParam(7));
    put(7, *doc, 1);
    expect_tensor_attr_calls(0, 0);
    expect_shared_executor_tasks(1);
    assertExecuteHistory({0});
}

TEST_F(TwoPhasePutTest, document_is_cleared_if_field_is_not_set)
{
    auto doc = make_no_field_doc();
    put(1, *doc, 1);
    expect_tensor_attr_calls(0, 0, 1);
    expect_shared_executor_tasks(1);
    assertExecuteHistory({0});
}

TEST_F(TwoPhasePutTest, document_is_cleared_if_tensor_in_field_is_not_set)
{
    auto doc = make_no_tensor_doc();
    put(1, *doc, 1);
    expect_tensor_attr_calls(0, 0, 1);
    expect_shared_executor_tasks(1);
    assertExecuteHistory({0});
}

TEST_F(TwoPhasePutTest, handles_assign_update_as_two_phase_put_when_specified_for_tensor_attribute)
{
    auto upd = make_assign_update();

    DummyFieldUpdateCallback on_update;
    update(1, *upd, 1, on_update);
    expect_tensor_attr_calls(1, 1);
    expect_shared_executor_tasks(1);
    assertExecuteHistory({0});

    update(2, *upd, 2, on_update);
    expect_tensor_attr_calls(2, 2);
    expect_shared_executor_tasks(2);
    assertExecuteHistory({0, 0});
}


ImportedAttributeVector::SP
createImportedAttribute(const vespalib::string &name)
{
    auto result = ImportedAttributeVectorFactory::create(name,
                                                         std::shared_ptr<ReferenceAttribute>(),
                                                         std::shared_ptr<search::IDocumentMetaStoreContext>(),
                                                         AttributeVector::SP(),
                                                         std::shared_ptr<const search::IDocumentMetaStoreContext>(),
                                                         true);
    result->getSearchCache()->insert("foo", BitVectorSearchCache::Entry::SP());
    return result;
}

ImportedAttributesRepo::UP
createImportedAttributesRepo()
{
    auto result = std::make_unique<ImportedAttributesRepo>();
    result->add("imported_a", createImportedAttribute("imported_a"));
    result->add("imported_b", createImportedAttribute("imported_b"));
    return result;
}

TEST_F(AttributeWriterTest, forceCommit_clears_search_cache_in_imported_attribute_vectors)
{
    _mgr->setImportedAttributes(createImportedAttributesRepo());
    commit(10);
    EXPECT_EQ(0u, _mgr->getImportedAttributes()->get("imported_a")->getSearchCache()->size());
    EXPECT_EQ(0u, _mgr->getImportedAttributes()->get("imported_b")->getSearchCache()->size());
}

TEST_F(AttributeWriterTest, ignores_force_commit_serial_not_greater_than_create_serial)
{
    auto a1 = addAttribute("a1");
    allocAttributeWriter();
    a1->setCreateSerialNum(100);
    EXPECT_EQ(0u, test_force_commit(*a1, 50u));
    EXPECT_EQ(0u, test_force_commit(*a1, 100u));
    EXPECT_EQ(150u, test_force_commit(*a1, 150u));
}

class StructWriterTestBase : public AttributeWriterTest {
public:
    DocumentType _type;
    const Field _valueField;
    StructDataType _structFieldType;

    StructWriterTestBase()
        : AttributeWriterTest(),
          _type("test"),
          _valueField("value", 2, *DataType::INT),
          _structFieldType("struct")
    {
        addAttribute({"value", AVConfig(AVBasicType::INT32, AVCollectionType::SINGLE)});
        _type.addField(_valueField);
        _structFieldType.addField(_valueField);
    }
    ~StructWriterTestBase();

    std::unique_ptr<StructFieldValue> makeStruct() {
        return std::make_unique<StructFieldValue>(_structFieldType);
    }

    std::unique_ptr<StructFieldValue> makeStruct(const int32_t value) {
        auto ret = makeStruct();
        ret->setValue(_valueField, IntFieldValue(value));
        return ret;
    }

    std::unique_ptr<Document> makeDoc() {
        return Document::make_without_repo(_type, DocumentId("id::test::1"));
    }
};

StructWriterTestBase::~StructWriterTestBase() = default;

class StructArrayWriterTest : public StructWriterTestBase {
public:
    using StructWriterTestBase::makeDoc;
    const ArrayDataType _structArrayFieldType;
    const Field _structArrayField;

    StructArrayWriterTest()
        : StructWriterTestBase(),
          _structArrayFieldType(_structFieldType),
          _structArrayField("array", _structArrayFieldType)
    {
        addAttribute({"array.value", AVConfig(AVBasicType::INT32, AVCollectionType::ARRAY)});
        _type.addField(_structArrayField);
    }
    ~StructArrayWriterTest();

    std::unique_ptr<Document> makeDoc(int32_t value, const std::vector<int32_t> &arrayValues) {
        auto doc = makeDoc();
        doc->setValue(_valueField, IntFieldValue(value));
        ArrayFieldValue s(_structArrayFieldType);
        for (const auto &arrayValue : arrayValues) {
            s.add(*makeStruct(arrayValue));
        }
        doc->setValue(_structArrayField, s);
        return doc;
    }
    void checkAttrs(uint32_t lid, int32_t value, const std::vector<int32_t> &arrayValues) {
        auto valueAttr = _mgr->getAttribute("value")->getSP();
        auto arrayValueAttr = _mgr->getAttribute("array.value")->getSP();
        EXPECT_EQ(value, valueAttr->getInt(lid));
        attribute::IntegerContent ibuf;
        ibuf.fill(*arrayValueAttr, lid);
        EXPECT_EQ(arrayValues.size(), ibuf.size());
        for (size_t i = 0; i < arrayValues.size(); ++i) {
            EXPECT_EQ(arrayValues[i], ibuf[i]);
        }
    }
};

StructArrayWriterTest::~StructArrayWriterTest() = default;

TEST_F(StructArrayWriterTest, update_with_doc_argument_updates_struct_field_attributes)
{
    allocAttributeWriter();
    auto doc = makeDoc(10,  {11, 12});
    put(10, *doc, 1);
    checkAttrs(1, 10, {11, 12});
    doc = makeDoc(20, {21});
    update(11, *doc, 1);
    checkAttrs(1, 10, {21});
}

class StructMapWriterTest : public StructWriterTestBase {
public:
    using StructWriterTestBase::makeDoc;
    const MapDataType _structMapFieldType;
    const Field _structMapField;

    StructMapWriterTest()
        : StructWriterTestBase(),
          _structMapFieldType(*DataType::INT, _structFieldType),
          _structMapField("map", _structMapFieldType)
    {
        addAttribute({"map.value.value", AVConfig(AVBasicType::INT32, AVCollectionType::ARRAY)});
        addAttribute({"map.key", AVConfig(AVBasicType::INT32, AVCollectionType::ARRAY)});
        _type.addField(_structMapField);
    }

    std::unique_ptr<Document> makeDoc(int32_t value, const std::map<int32_t, int32_t> &mapValues) {
        auto doc = makeDoc();
        doc->setValue(_valueField, IntFieldValue(value));
        MapFieldValue s(_structMapFieldType);
        for (const auto &mapValue : mapValues) {
            s.put(IntFieldValue(mapValue.first), *makeStruct(mapValue.second));
        }
        doc->setValue(_structMapField, s);
        return doc;
    }

    void checkAttrs(uint32_t lid, int32_t expValue, const std::map<int32_t, int32_t> &expMap) {
        auto valueAttr = _mgr->getAttribute("value")->getSP();
        auto mapKeyAttr = _mgr->getAttribute("map.key")->getSP();
        auto mapValueAttr = _mgr->getAttribute("map.value.value")->getSP();
        EXPECT_EQ(expValue, valueAttr->getInt(lid));
        attribute::IntegerContent mapKeys;
        mapKeys.fill(*mapKeyAttr, lid);
        attribute::IntegerContent mapValues;
        mapValues.fill(*mapValueAttr, lid);
        EXPECT_EQ(expMap.size(), mapValues.size());
        EXPECT_EQ(expMap.size(), mapKeys.size());
        size_t i = 0;
        for (const auto &expMapElem : expMap) {
            EXPECT_EQ(expMapElem.first, mapKeys[i]);
            EXPECT_EQ(expMapElem.second, mapValues[i]);
            ++i;
        }
    }
};

TEST_F(StructMapWriterTest, update_with_doc_argument_updates_struct_field_attributes)
{
    allocAttributeWriter();
    auto doc = makeDoc(10,  {{1, 11}, {2, 12}});
    put(10, *doc, 1);
    checkAttrs(1, 10, {{1, 11}, {2, 12}});
    doc = makeDoc(20, {{42, 21}});
    update(11, *doc, 1);
    checkAttrs(1, 10, {{42, 21}});
}

GTEST_MAIN_RUN_ALL_TESTS()
