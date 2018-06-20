// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/log.h>
LOG_SETUP("attribute_test");

#include <vespa/config-attributes.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/predicate/predicate_slime_builder.h>
#include <vespa/document/update/arithmeticvalueupdate.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/eval/tensor/default_tensor.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/eval/tensor/tensor_factory.h>
#include <vespa/eval/tensor/types.h>
#include <vespa/fastos/file.h>
#include <vespa/searchcommon/attribute/attributecontent.h>
#include <vespa/searchcore/proton/attribute/attribute_collection_spec_factory.h>
#include <vespa/searchcore/proton/attribute/attribute_writer.h>
#include <vespa/searchcore/proton/attribute/ifieldupdatecallback.h>
#include <vespa/searchcore/proton/attribute/attributemanager.h>
#include <vespa/searchcore/proton/attribute/filter_attribute_manager.h>
#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>
#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/searchcore/proton/test/attribute_utils.h>
#include <vespa/searchcorespi/flush/iflushtarget.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/attributevector.hpp>
#include <vespa/searchlib/attribute/bitvector_search_cache.h>
#include <vespa/searchlib/attribute/imported_attribute_vector.h>
#include <vespa/searchlib/attribute/imported_attribute_vector_factory.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/predicate_attribute.h>
#include <vespa/searchlib/attribute/singlenumericattribute.hpp>
#include <vespa/searchlib/common/foregroundtaskexecutor.h>
#include <vespa/searchlib/common/idestructorcallback.h>
#include <vespa/searchlib/common/sequencedtaskexecutorobserver.h>
#include <vespa/searchlib/index/docbuilder.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/predicate/predicate_hash.h>
#include <vespa/searchlib/predicate/predicate_index.h>
#include <vespa/searchlib/tensor/tensor_attribute.h>
#include <vespa/searchlib/test/directory_handler.h>
#include <vespa/searchlib/util/filekit.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchcommon/attribute/iattributevector.h>

namespace vespa { namespace config { namespace search {}}}

using namespace config;
using namespace document;
using namespace proton;
using namespace search::index;
using namespace search;
using namespace vespa::config::search;

using proton::ImportedAttributesRepo;
using proton::test::AttributeUtils;
using search::TuneFileAttributes;
using search::attribute::BitVectorSearchCache;
using search::attribute::IAttributeVector;
using search::attribute::ImportedAttributeVector;
using search::attribute::ImportedAttributeVectorFactory;
using search::attribute::ReferenceAttribute;
using search::index::DummyFileHeaderContext;
using search::index::schema::CollectionType;
using search::predicate::PredicateHash;
using search::predicate::PredicateIndex;
using search::tensor::TensorAttribute;
using search::test::DirectoryHandler;
using std::string;
using vespalib::eval::ValueType;
using vespalib::tensor::Tensor;
using vespalib::tensor::TensorCells;
using vespalib::tensor::TensorDimensions;

using AVConfig = search::attribute::Config;
using AVBasicType = search::attribute::BasicType;
using AVCollectionType = search::attribute::CollectionType;
using Int32AttributeVector = SingleValueNumericAttribute<IntegerAttributeTemplate<int32_t> >;
using LidVector = LidVectorContext::LidVector;

namespace
{

const uint64_t createSerialNum = 42u;

}

AVConfig
unregister(const AVConfig & cfg)
{
    AVConfig retval = cfg;
    return retval;
}

const string test_dir = "test_output";
const AVConfig INT32_SINGLE = unregister(AVConfig(AVBasicType::INT32));
const AVConfig INT32_ARRAY = unregister(AVConfig(AVBasicType::INT32, AVCollectionType::ARRAY));

void
fillAttribute(const AttributeVector::SP &attr, uint32_t numDocs, int64_t value, uint64_t lastSyncToken)
{
    AttributeUtils::fillAttribute(attr, numDocs, value, lastSyncToken);
}

void
fillAttribute(const AttributeVector::SP &attr, uint32_t from, uint32_t to, int64_t value, uint64_t lastSyncToken)
{
    AttributeUtils::fillAttribute(attr, from, to, value, lastSyncToken);
}

const std::shared_ptr<IDestructorCallback> emptyCallback;


struct Fixture
{
    DirectoryHandler _dirHandler;
    DummyFileHeaderContext   _fileHeaderContext;
    ForegroundTaskExecutor   _attributeFieldWriterReal;
    SequencedTaskExecutorObserver _attributeFieldWriter;
    HwInfo                   _hwInfo;
    proton::AttributeManager::SP _m;
    std::unique_ptr<AttributeWriter> _aw;

    Fixture(uint32_t threads)
        : _dirHandler(test_dir),
          _fileHeaderContext(),
          _attributeFieldWriterReal(threads),
          _attributeFieldWriter(_attributeFieldWriterReal),
          _hwInfo(),
          _m(std::make_shared<proton::AttributeManager>
             (test_dir, "test.subdb", TuneFileAttributes(),
              _fileHeaderContext, _attributeFieldWriter, _hwInfo)),
          _aw()
    {
        allocAttributeWriter();
    }
    Fixture()
        : Fixture(1)
    {
    }
    ~Fixture();
    void allocAttributeWriter() {
        _aw = std::make_unique<AttributeWriter>(_m);
    }
    AttributeVector::SP addAttribute(const vespalib::string &name) {
        return addAttribute({name, AVConfig(AVBasicType::INT32)}, createSerialNum);
    }
    AttributeVector::SP addAttribute(const AttributeSpec &spec, SerialNum serialNum) {
        auto ret = _m->addAttribute(spec, serialNum);
        allocAttributeWriter();
        return ret;
    }
    void put(SerialNum serialNum, const Document &doc, DocumentIdT lid,
             bool immediateCommit = true) {
        _aw->put(serialNum, doc, lid, immediateCommit, emptyCallback);
    }
    void update(SerialNum serialNum, const DocumentUpdate &upd,
                DocumentIdT lid, bool immediateCommit, IFieldUpdateCallback & onUpdate) {
        _aw->update(serialNum, upd, lid, immediateCommit, emptyCallback, onUpdate);
    }
    void update(SerialNum serialNum, const Document &doc,
                DocumentIdT lid, bool immediateCommit) {
        _aw->update(serialNum, doc, lid, immediateCommit, emptyCallback);
    }
    void remove(SerialNum serialNum, DocumentIdT lid, bool immediateCommit = true) {
        _aw->remove(serialNum, lid, immediateCommit, emptyCallback);
    }
    void remove(const LidVector &lidVector, SerialNum serialNum, bool immediateCommit = true) {
        _aw->remove(lidVector, serialNum, immediateCommit, emptyCallback);
    }
    void commit(SerialNum serialNum) {
        _aw->forceCommit(serialNum, emptyCallback);
    }
    void assertExecuteHistory(std::vector<uint32_t> expExecuteHistory) {
        EXPECT_EQUAL(expExecuteHistory, _attributeFieldWriter.getExecuteHistory());
    }
};

Fixture::~Fixture() = default;

TEST_F("require that attribute writer handles put", Fixture)
{
    Schema s;
    s.addAttributeField(Schema::AttributeField("a1", schema::DataType::INT32, CollectionType::SINGLE));
    s.addAttributeField(Schema::AttributeField("a2", schema::DataType::INT32, CollectionType::ARRAY));
    s.addAttributeField(Schema::AttributeField("a3", schema::DataType::FLOAT, CollectionType::SINGLE));
    s.addAttributeField(Schema::AttributeField("a4", schema::DataType::STRING, CollectionType::SINGLE));

    DocBuilder idb(s);

    AttributeVector::SP a1 = f.addAttribute("a1");
    AttributeVector::SP a2 = f.addAttribute({"a2", AVConfig(AVBasicType::INT32, AVCollectionType::ARRAY)}, createSerialNum);
    AttributeVector::SP a3 = f.addAttribute({"a3", AVConfig(AVBasicType::FLOAT)}, createSerialNum);
    AttributeVector::SP a4 = f.addAttribute({"a4", AVConfig(AVBasicType::STRING)}, createSerialNum);

    attribute::IntegerContent ibuf;
    attribute::FloatContent fbuf;
    attribute::ConstCharContent sbuf;
    { // empty document should give default values
        EXPECT_EQUAL(1u, a1->getNumDocs());
        f.put(1, *idb.startDocument("doc::1").endDocument(), 1);
        EXPECT_EQUAL(2u, a1->getNumDocs());
        EXPECT_EQUAL(2u, a2->getNumDocs());
        EXPECT_EQUAL(2u, a3->getNumDocs());
        EXPECT_EQUAL(2u, a4->getNumDocs());
        EXPECT_EQUAL(1u, a1->getStatus().getLastSyncToken());
        EXPECT_EQUAL(1u, a2->getStatus().getLastSyncToken());
        EXPECT_EQUAL(1u, a3->getStatus().getLastSyncToken());
        EXPECT_EQUAL(1u, a4->getStatus().getLastSyncToken());
        ibuf.fill(*a1, 1);
        EXPECT_EQUAL(1u, ibuf.size());
        EXPECT_TRUE(search::attribute::isUndefined<int32_t>(ibuf[0]));
        ibuf.fill(*a2, 1);
        EXPECT_EQUAL(0u, ibuf.size());
        fbuf.fill(*a3, 1);
        EXPECT_EQUAL(1u, fbuf.size());
        EXPECT_TRUE(search::attribute::isUndefined<float>(fbuf[0]));
        sbuf.fill(*a4, 1);
        EXPECT_EQUAL(1u, sbuf.size());
        EXPECT_EQUAL(strcmp("", sbuf[0]), 0);
    }
    { // document with single value & multi value attribute
        Document::UP doc = idb.startDocument("doc::2").
            startAttributeField("a1").addInt(10).endField().
            startAttributeField("a2").startElement().addInt(20).endElement().
                                      startElement().addInt(30).endElement().endField().endDocument();
        f.put(2, *doc, 2);
        EXPECT_EQUAL(3u, a1->getNumDocs());
        EXPECT_EQUAL(3u, a2->getNumDocs());
        EXPECT_EQUAL(2u, a1->getStatus().getLastSyncToken());
        EXPECT_EQUAL(2u, a2->getStatus().getLastSyncToken());
        EXPECT_EQUAL(2u, a3->getStatus().getLastSyncToken());
        EXPECT_EQUAL(2u, a4->getStatus().getLastSyncToken());
        ibuf.fill(*a1, 2);
        EXPECT_EQUAL(1u, ibuf.size());
        EXPECT_EQUAL(10u, ibuf[0]);
        ibuf.fill(*a2, 2);
        EXPECT_EQUAL(2u, ibuf.size());
        EXPECT_EQUAL(20u, ibuf[0]);
        EXPECT_EQUAL(30u, ibuf[1]);
    }
    { // replace existing document
        Document::UP doc = idb.startDocument("doc::2").
            startAttributeField("a1").addInt(100).endField().
            startAttributeField("a2").startElement().addInt(200).endElement().
                                      startElement().addInt(300).endElement().
                                      startElement().addInt(400).endElement().endField().endDocument();
        f.put(3, *doc, 2);
        EXPECT_EQUAL(3u, a1->getNumDocs());
        EXPECT_EQUAL(3u, a2->getNumDocs());
        EXPECT_EQUAL(3u, a1->getStatus().getLastSyncToken());
        EXPECT_EQUAL(3u, a2->getStatus().getLastSyncToken());
        EXPECT_EQUAL(3u, a3->getStatus().getLastSyncToken());
        EXPECT_EQUAL(3u, a4->getStatus().getLastSyncToken());
        ibuf.fill(*a1, 2);
        EXPECT_EQUAL(1u, ibuf.size());
        EXPECT_EQUAL(100u, ibuf[0]);
        ibuf.fill(*a2, 2);
        EXPECT_EQUAL(3u, ibuf.size());
        EXPECT_EQUAL(200u, ibuf[0]);
        EXPECT_EQUAL(300u, ibuf[1]);
        EXPECT_EQUAL(400u, ibuf[2]);
    }
}

TEST_F("require that attribute writer handles predicate put", Fixture)
{
    Schema s;
    s.addAttributeField(Schema::AttributeField("a1", schema::DataType::BOOLEANTREE, CollectionType::SINGLE));
    DocBuilder idb(s);

    AttributeVector::SP a1 = f.addAttribute({"a1", AVConfig(AVBasicType::PREDICATE)}, createSerialNum);

    PredicateIndex &index = static_cast<PredicateAttribute &>(*a1).getIndex();

    // empty document should give default values
    EXPECT_EQUAL(1u, a1->getNumDocs());
    f.put(1, *idb.startDocument("doc::1").endDocument(), 1);
    EXPECT_EQUAL(2u, a1->getNumDocs());
    EXPECT_EQUAL(1u, a1->getStatus().getLastSyncToken());
    EXPECT_EQUAL(0u, index.getZeroConstraintDocs().size());

    // document with single value attribute
    PredicateSlimeBuilder builder;
    Document::UP doc =
        idb.startDocument("doc::2").startAttributeField("a1")
        .addPredicate(builder.true_predicate().build())
        .endField().endDocument();
    f.put(2, *doc, 2);
    EXPECT_EQUAL(3u, a1->getNumDocs());
    EXPECT_EQUAL(2u, a1->getStatus().getLastSyncToken());
    EXPECT_EQUAL(1u, index.getZeroConstraintDocs().size());

    auto it = index.getIntervalIndex().lookup(PredicateHash::hash64("foo=bar"));
    EXPECT_FALSE(it.valid());

    // replace existing document
    doc = idb.startDocument("doc::2").startAttributeField("a1")
          .addPredicate(builder.feature("foo").value("bar").build())
          .endField().endDocument();
    f.put(3, *doc, 2);
    EXPECT_EQUAL(3u, a1->getNumDocs());
    EXPECT_EQUAL(3u, a1->getStatus().getLastSyncToken());

    it = index.getIntervalIndex().lookup(PredicateHash::hash64("foo=bar"));
    EXPECT_TRUE(it.valid());
}

void
assertUndefined(const IAttributeVector &attr, uint32_t docId)
{
    EXPECT_TRUE(search::attribute::isUndefined<int32_t>(attr.getInt(docId)));
}

TEST_F("require that attribute writer handles remove", Fixture)
{
    AttributeVector::SP a1 = f.addAttribute("a1");
    AttributeVector::SP a2 = f.addAttribute("a2");
    fillAttribute(a1, 1, 10, 1);
    fillAttribute(a2, 1, 20, 1);

    f.remove(2, 0);

    TEST_DO(assertUndefined(*a1, 0));
    TEST_DO(assertUndefined(*a2, 0));

    f.remove(2, 0); // same sync token as previous
    try {
        f.remove(1, 0); // lower sync token than previous
        EXPECT_TRUE(true);  // update is ignored
    } catch (vespalib::IllegalStateException & e) {
        LOG(info, "Got expected exception: '%s'", e.getMessage().c_str());
        EXPECT_TRUE(true);
    }
}

TEST_F("require that attribute writer handles batch remove", Fixture)
{
    AttributeVector::SP a1 = f.addAttribute("a1");
    AttributeVector::SP a2 = f.addAttribute("a2");
    fillAttribute(a1, 4, 22, 1);
    fillAttribute(a2, 4, 33, 1);

    LidVector lidsToRemove = {1,3};
    f.remove(lidsToRemove, 2);

    TEST_DO(assertUndefined(*a1, 1));
    EXPECT_EQUAL(22, a1->getInt(2));
    TEST_DO(assertUndefined(*a1, 3));
    TEST_DO(assertUndefined(*a2, 1));
    EXPECT_EQUAL(33, a2->getInt(2));
    TEST_DO(assertUndefined(*a2, 3));
}

void verifyAttributeContent(const AttributeVector & v, uint32_t lid, vespalib::stringref expected)
{
    attribute::ConstCharContent sbuf;
    sbuf.fill(v, lid);
    EXPECT_EQUAL(1u, sbuf.size());
    EXPECT_EQUAL(expected, sbuf[0]);
}

TEST_F("require that visibilitydelay is honoured", Fixture)
{
    AttributeVector::SP a1 = f.addAttribute({"a1", AVConfig(AVBasicType::STRING)}, createSerialNum);
    Schema s;
    s.addAttributeField(Schema::AttributeField("a1", schema::DataType::STRING, CollectionType::SINGLE));
    DocBuilder idb(s);
    EXPECT_EQUAL(1u, a1->getNumDocs());
    EXPECT_EQUAL(0u, a1->getStatus().getLastSyncToken());
    Document::UP doc = idb.startDocument("doc::1")
                              .startAttributeField("a1").addStr("10").endField()
                          .endDocument();
    f.put(3, *doc, 1);
    EXPECT_EQUAL(2u, a1->getNumDocs());
    EXPECT_EQUAL(3u, a1->getStatus().getLastSyncToken());
    AttributeWriter awDelayed(f._m);
    awDelayed.put(4, *doc, 2, false, emptyCallback);
    EXPECT_EQUAL(3u, a1->getNumDocs());
    EXPECT_EQUAL(3u, a1->getStatus().getLastSyncToken());
    awDelayed.put(5, *doc, 4, false, emptyCallback);
    EXPECT_EQUAL(5u, a1->getNumDocs());
    EXPECT_EQUAL(3u, a1->getStatus().getLastSyncToken());
    awDelayed.forceCommit(6, emptyCallback);
    EXPECT_EQUAL(6u, a1->getStatus().getLastSyncToken());

    AttributeWriter awDelayedShort(f._m);
    awDelayedShort.put(7, *doc, 2, false, emptyCallback);
    EXPECT_EQUAL(6u, a1->getStatus().getLastSyncToken());
    awDelayedShort.put(8, *doc, 2, false, emptyCallback);
    awDelayedShort.forceCommit(8, emptyCallback);
    EXPECT_EQUAL(8u, a1->getStatus().getLastSyncToken());

    verifyAttributeContent(*a1, 2, "10");
    awDelayed.put(9, *idb.startDocument("doc::1").startAttributeField("a1").addStr("11").endField().endDocument(),
            2, false, emptyCallback);
    awDelayed.put(10, *idb.startDocument("doc::1").startAttributeField("a1").addStr("20").endField().endDocument(),
            2, false, emptyCallback);
    awDelayed.put(11, *idb.startDocument("doc::1").startAttributeField("a1").addStr("30").endField().endDocument(),
            2, false, emptyCallback);
    EXPECT_EQUAL(8u, a1->getStatus().getLastSyncToken());
    verifyAttributeContent(*a1, 2, "10");
    awDelayed.forceCommit(12, emptyCallback);
    EXPECT_EQUAL(12u, a1->getStatus().getLastSyncToken());
    verifyAttributeContent(*a1, 2, "30");
    
}

TEST_F("require that attribute writer handles predicate remove", Fixture)
{
    AttributeVector::SP a1 = f.addAttribute({"a1", AVConfig(AVBasicType::PREDICATE)}, createSerialNum);
    Schema s;
    s.addAttributeField(
            Schema::AttributeField("a1", schema::DataType::BOOLEANTREE, CollectionType::SINGLE));

    DocBuilder idb(s);
    PredicateSlimeBuilder builder;
    Document::UP doc =
        idb.startDocument("doc::1").startAttributeField("a1")
        .addPredicate(builder.true_predicate().build())
        .endField().endDocument();
    f.put(1, *doc, 1);
    EXPECT_EQUAL(2u, a1->getNumDocs());

    PredicateIndex &index = static_cast<PredicateAttribute &>(*a1).getIndex();
    EXPECT_EQUAL(1u, index.getZeroConstraintDocs().size());
    f.remove(2, 1);
    EXPECT_EQUAL(0u, index.getZeroConstraintDocs().size());
}

TEST_F("require that attribute writer handles update", Fixture)
{
    AttributeVector::SP a1 = f.addAttribute("a1");
    AttributeVector::SP a2 = f.addAttribute("a2");

    fillAttribute(a1, 1, 10, 1);
    fillAttribute(a2, 1, 20, 1);

    Schema schema;
    schema.addAttributeField(Schema::AttributeField("a1", schema::DataType::INT32, CollectionType::SINGLE));
    schema.addAttributeField(Schema::AttributeField("a2", schema::DataType::INT32, CollectionType::SINGLE));
    DocBuilder idb(schema);
    const document::DocumentType &dt(idb.getDocumentType());
    DocumentUpdate upd(*idb.getDocumentTypeRepo(), dt, DocumentId("doc::1"));
    upd.addUpdate(FieldUpdate(upd.getType().getField("a1"))
                  .addUpdate(ArithmeticValueUpdate(ArithmeticValueUpdate::Add, 5)));
    upd.addUpdate(FieldUpdate(upd.getType().getField("a2"))
                  .addUpdate(ArithmeticValueUpdate(ArithmeticValueUpdate::Add, 10)));

    DummyFieldUpdateCallback onUpdate;
    bool immediateCommit = true;
    f.update(2, upd, 1, immediateCommit, onUpdate);

    attribute::IntegerContent ibuf;
    ibuf.fill(*a1, 1);
    EXPECT_EQUAL(1u, ibuf.size());
    EXPECT_EQUAL(15u, ibuf[0]);
    ibuf.fill(*a2, 1);
    EXPECT_EQUAL(1u, ibuf.size());
    EXPECT_EQUAL(30u, ibuf[0]);

    f.update(2, upd, 1, immediateCommit, onUpdate); // same sync token as previous
    try {
        f.update(1, upd, 1, immediateCommit, onUpdate); // lower sync token than previous
        EXPECT_TRUE(true);  // update is ignored
    } catch (vespalib::IllegalStateException & e) {
        LOG(info, "Got expected exception: '%s'", e.getMessage().c_str());
        EXPECT_TRUE(true);
    }
}

TEST_F("require that attribute writer handles predicate update", Fixture)
{
    AttributeVector::SP a1 = f.addAttribute({"a1", AVConfig(AVBasicType::PREDICATE)}, createSerialNum);
    Schema schema;
    schema.addAttributeField(Schema::AttributeField("a1", schema::DataType::BOOLEANTREE, CollectionType::SINGLE));

    DocBuilder idb(schema);
    PredicateSlimeBuilder builder;
    Document::UP doc =
        idb.startDocument("doc::1").startAttributeField("a1")
        .addPredicate(builder.true_predicate().build())
        .endField().endDocument();
    f.put(1, *doc, 1);
    EXPECT_EQUAL(2u, a1->getNumDocs());

    const document::DocumentType &dt(idb.getDocumentType());
    DocumentUpdate upd(*idb.getDocumentTypeRepo(), dt, DocumentId("doc::1"));
    PredicateFieldValue new_value(builder.feature("foo").value("bar").build());
    upd.addUpdate(FieldUpdate(upd.getType().getField("a1"))
                  .addUpdate(AssignValueUpdate(new_value)));

    PredicateIndex &index = static_cast<PredicateAttribute &>(*a1).getIndex();
    EXPECT_EQUAL(1u, index.getZeroConstraintDocs().size());
    EXPECT_FALSE(index.getIntervalIndex().lookup(PredicateHash::hash64("foo=bar")).valid());
    bool immediateCommit = true;
    DummyFieldUpdateCallback onUpdate;
    f.update(2, upd, 1, immediateCommit, onUpdate);
    EXPECT_EQUAL(0u, index.getZeroConstraintDocs().size());
    EXPECT_TRUE(index.getIntervalIndex().lookup(PredicateHash::hash64("foo=bar")).valid());
}

struct AttributeCollectionSpecFixture
{
    AttributesConfigBuilder _builder;
    AttributeCollectionSpecFactory _factory;
    AttributeCollectionSpecFixture(bool fastAccessOnly)
        : _builder(),
          _factory(search::GrowStrategy(), 100, fastAccessOnly)
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
    AttributeCollectionSpec::UP create(uint32_t docIdLimit,
                                       search::SerialNum serialNum) {
        return _factory.create(_builder, docIdLimit, serialNum);
    }
};

struct NormalAttributeCollectionSpecFixture : public AttributeCollectionSpecFixture
{
    NormalAttributeCollectionSpecFixture() : AttributeCollectionSpecFixture(false) {}
};

struct FastAccessAttributeCollectionSpecFixture : public AttributeCollectionSpecFixture
{
    FastAccessAttributeCollectionSpecFixture() : AttributeCollectionSpecFixture(true) {}
};

TEST_F("require that normal attribute collection spec can be created",
        NormalAttributeCollectionSpecFixture)
{
    AttributeCollectionSpec::UP spec = f.create(10, 20);
    EXPECT_EQUAL(2u, spec->getAttributes().size());
    EXPECT_EQUAL("a1", spec->getAttributes()[0].getName());
    EXPECT_EQUAL("a2", spec->getAttributes()[1].getName());
    EXPECT_EQUAL(10u, spec->getDocIdLimit());
    EXPECT_EQUAL(20u, spec->getCurrentSerialNum());
}

TEST_F("require that fast access attribute collection spec can be created",
        FastAccessAttributeCollectionSpecFixture)
{
    AttributeCollectionSpec::UP spec = f.create(10, 20);
    EXPECT_EQUAL(1u, spec->getAttributes().size());
    EXPECT_EQUAL("a2", spec->getAttributes()[0].getName());
    EXPECT_EQUAL(10u, spec->getDocIdLimit());
    EXPECT_EQUAL(20u, spec->getCurrentSerialNum());
}

const FilterAttributeManager::AttributeSet ACCEPTED_ATTRIBUTES = {"a2"};

struct FilterFixture
{
    DirectoryHandler _dirHandler;
    DummyFileHeaderContext _fileHeaderContext;
    ForegroundTaskExecutor _attributeFieldWriter;
    HwInfo                 _hwInfo;

    proton::AttributeManager::SP _baseMgr;
    FilterAttributeManager _filterMgr;
    FilterFixture()
        : _dirHandler(test_dir),
          _fileHeaderContext(),
          _attributeFieldWriter(),
          _hwInfo(),
          _baseMgr(new proton::AttributeManager(test_dir, "test.subdb",
                                                TuneFileAttributes(),
                                                _fileHeaderContext,
                                                _attributeFieldWriter,
                                                _hwInfo)),
          _filterMgr(ACCEPTED_ATTRIBUTES, _baseMgr)
    {
        _baseMgr->addAttribute({"a1", INT32_SINGLE}, createSerialNum);
        _baseMgr->addAttribute({"a2", INT32_SINGLE}, createSerialNum);
   }
};

TEST_F("require that filter attribute manager can filter attributes", FilterFixture)
{
    EXPECT_TRUE(f._filterMgr.getAttribute("a1").get() == NULL);
    EXPECT_TRUE(f._filterMgr.getAttribute("a2").get() != NULL);
    std::vector<AttributeGuard> attrs;
    f._filterMgr.getAttributeList(attrs);
    EXPECT_EQUAL(1u, attrs.size());
    EXPECT_EQUAL("a2", attrs[0]->getName());
    searchcorespi::IFlushTarget::List targets = f._filterMgr.getFlushTargets();
    EXPECT_EQUAL(2u, targets.size());
    EXPECT_EQUAL("attribute.flush.a2", targets[0]->getName());
    EXPECT_EQUAL("attribute.shrink.a2", targets[1]->getName());
}

TEST_F("require that filter attribute manager can return flushed serial number", FilterFixture)
{
    f._baseMgr->flushAll(100);
    EXPECT_EQUAL(0u, f._filterMgr.getFlushedSerialNum("a1"));
    EXPECT_EQUAL(100u, f._filterMgr.getFlushedSerialNum("a2"));
}

namespace {

Tensor::UP
createTensor(const TensorCells &cells, const TensorDimensions &dimensions) {
    vespalib::tensor::DefaultTensor::builder builder;
    return vespalib::tensor::TensorFactory::create(cells, dimensions, builder);
}


AttributeVector::SP
createTensorAttribute(Fixture &f) {
    AVConfig cfg(AVBasicType::TENSOR);
    cfg.setTensorType(ValueType::from_spec("tensor(x{},y{})"));
    auto ret = f.addAttribute({"a1", cfg}, createSerialNum);
    return ret;
}

Schema
createTensorSchema() {
    Schema schema;
    schema.addAttributeField(Schema::AttributeField("a1", schema::DataType::TENSOR, CollectionType::SINGLE));
    return schema;
}

Document::UP
createTensorPutDoc(DocBuilder &builder, const Tensor &tensor) {
    return builder.startDocument("doc::1").
        startAttributeField("a1").
        addTensor(tensor.clone()).endField().endDocument();
}

}


TEST_F("Test that we can use attribute writer to write to tensor attribute",
       Fixture)
{
    AttributeVector::SP a1 = createTensorAttribute(f);
    Schema s = createTensorSchema();
    DocBuilder builder(s);
    auto tensor = createTensor({ {{{"x", "4"}, {"y", "5"}}, 7} },
                               {"x", "y"});
    Document::UP doc = createTensorPutDoc(builder, *tensor);
    f.put(1, *doc, 1);
    EXPECT_EQUAL(2u, a1->getNumDocs());
    TensorAttribute *tensorAttribute =
        dynamic_cast<TensorAttribute *>(a1.get());
    EXPECT_TRUE(tensorAttribute != nullptr);
    auto tensor2 = tensorAttribute->getTensor(1);
    EXPECT_TRUE(static_cast<bool>(tensor2));
    EXPECT_TRUE(tensor->equals(*tensor2));
}

TEST_F("require that attribute writer handles tensor assign update", Fixture)
{
    AttributeVector::SP a1 = createTensorAttribute(f);
    Schema s = createTensorSchema();
    DocBuilder builder(s);
    auto tensor = createTensor({ {{{"x", "6"}, {"y", "7"}}, 9} },
                               {"x", "y"});
    Document::UP doc = createTensorPutDoc(builder, *tensor);
    f.put(1, *doc, 1);
    EXPECT_EQUAL(2u, a1->getNumDocs());
    TensorAttribute *tensorAttribute =
        dynamic_cast<TensorAttribute *>(a1.get());
    EXPECT_TRUE(tensorAttribute != nullptr);
    auto tensor2 = tensorAttribute->getTensor(1);
    EXPECT_TRUE(static_cast<bool>(tensor2));
    EXPECT_TRUE(tensor->equals(*tensor2));

    const document::DocumentType &dt(builder.getDocumentType());
    DocumentUpdate upd(*builder.getDocumentTypeRepo(), dt, DocumentId("doc::1"));
    auto new_tensor = createTensor({ {{{"x", "8"}, {"y", "9"}}, 11} },
                                   {"x", "y"});
    TensorFieldValue new_value;
    new_value = new_tensor->clone();
    upd.addUpdate(FieldUpdate(upd.getType().getField("a1"))
                  .addUpdate(AssignValueUpdate(new_value)));
    bool immediateCommit = true;
    DummyFieldUpdateCallback onUpdate;
    f.update(2, upd, 1, immediateCommit, onUpdate);
    EXPECT_EQUAL(2u, a1->getNumDocs());
    EXPECT_TRUE(tensorAttribute != nullptr);
    tensor2 = tensorAttribute->getTensor(1);
    EXPECT_TRUE(static_cast<bool>(tensor2));
    EXPECT_TRUE(!tensor->equals(*tensor2));
    EXPECT_TRUE(new_tensor->equals(*tensor2));

}

namespace {

void
assertPutDone(AttributeVector &attr, int32_t expVal)
{
    EXPECT_EQUAL(2u, attr.getNumDocs());
    EXPECT_EQUAL(1u, attr.getStatus().getLastSyncToken());
    attribute::IntegerContent ibuf;
    ibuf.fill(attr, 1);
    EXPECT_EQUAL(1u, ibuf.size());
    EXPECT_EQUAL(expVal, ibuf[0]);
}

void
putAttributes(Fixture &f, std::vector<uint32_t> expExecuteHistory)
{
    Schema s;
    s.addAttributeField(Schema::AttributeField("a1", schema::DataType::INT32, CollectionType::SINGLE));
    s.addAttributeField(Schema::AttributeField("a2", schema::DataType::INT32, CollectionType::SINGLE));
    s.addAttributeField(Schema::AttributeField("a3", schema::DataType::INT32, CollectionType::SINGLE));

    DocBuilder idb(s);

    AttributeVector::SP a1 = f.addAttribute("a1");
    AttributeVector::SP a2 = f.addAttribute("a2");
    AttributeVector::SP a3 = f.addAttribute("a3");

    EXPECT_EQUAL(1u, a1->getNumDocs());
    EXPECT_EQUAL(1u, a2->getNumDocs());
    EXPECT_EQUAL(1u, a3->getNumDocs());
    f.put(1, *idb.startDocument("doc::1").
          startAttributeField("a1").addInt(10).endField().
          startAttributeField("a2").addInt(15).endField().
          startAttributeField("a3").addInt(20).endField().
          endDocument(), 1);
    TEST_DO(assertPutDone(*a1, 10));
    TEST_DO(assertPutDone(*a2, 15));
    TEST_DO(assertPutDone(*a3, 20));
    TEST_DO(f.assertExecuteHistory(expExecuteHistory));
}

}

TEST_F("require that attribute writer spreads write over 1 write context", Fixture(1))
{
    TEST_DO(putAttributes(f, {0}));
}

TEST_F("require that attribute writer spreads write over 2 write contexts", Fixture(2))
{
    TEST_DO(putAttributes(f, {0, 1}));
}

TEST_F("require that attribute writer spreads write over 3 write contexts", Fixture(8))
{
    TEST_DO(putAttributes(f, {0, 1, 2}));
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

TEST_F("require that AttributeWriter::forceCommit() clears search cache in imported attribute vectors", Fixture)
{
    f._m->setImportedAttributes(createImportedAttributesRepo());
    f.commit(10);
    EXPECT_EQUAL(0u, f._m->getImportedAttributes()->get("imported_a")->getSearchCache()->size());
    EXPECT_EQUAL(0u, f._m->getImportedAttributes()->get("imported_b")->getSearchCache()->size());
}

struct StructFixtureBase : public Fixture
{
    DocumentType _type;
    const Field _valueField;
    StructDataType _structFieldType;

    StructFixtureBase()
        : Fixture(),
          _type("test"),
          _valueField("value", 2, *DataType::INT, true),
          _structFieldType("struct")
    {
        addAttribute({"value", AVConfig(AVBasicType::INT32, AVCollectionType::SINGLE)}, createSerialNum);
        _type.addField(_valueField);
        _structFieldType.addField(_valueField);
    }
    ~StructFixtureBase();

    std::unique_ptr<StructFieldValue>
    makeStruct()
    {
        return std::make_unique<StructFieldValue>(_structFieldType);
    }

    std::unique_ptr<StructFieldValue>
    makeStruct(const int32_t value)
    {
        auto ret = makeStruct();
        ret->setValue(_valueField, IntFieldValue(value));
        return ret;
    }

    std::unique_ptr<Document>
    makeDoc()
    {
        return std::make_unique<Document>(_type, DocumentId("id::test::1"));
    }
};

StructFixtureBase::~StructFixtureBase() = default;

struct StructArrayFixture : public StructFixtureBase
{
    using StructFixtureBase::makeDoc;
    const ArrayDataType _structArrayFieldType;
    const Field _structArrayField;

    StructArrayFixture()
        : StructFixtureBase(),
          _structArrayFieldType(_structFieldType),
          _structArrayField("array", _structArrayFieldType, true)
    {
        addAttribute({"array.value", AVConfig(AVBasicType::INT32, AVCollectionType::ARRAY)}, createSerialNum);
        _type.addField(_structArrayField);
    }
    ~StructArrayFixture();

    std::unique_ptr<Document>
    makeDoc(int32_t value, const std::vector<int32_t> &arrayValues)
    {
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
        auto valueAttr = _m->getAttribute("value")->getSP();
        auto arrayValueAttr = _m->getAttribute("array.value")->getSP();
        EXPECT_EQUAL(value, valueAttr->getInt(lid));
        attribute::IntegerContent ibuf;
        ibuf.fill(*arrayValueAttr, lid);
        EXPECT_EQUAL(arrayValues.size(), ibuf.size());
        for (size_t i = 0; i < arrayValues.size(); ++i) {
            EXPECT_EQUAL(arrayValues[i], ibuf[i]);
        }
    }
};

StructArrayFixture::~StructArrayFixture() = default;

TEST_F("require that update with doc argument updates struct field attributes (array)", StructArrayFixture)
{
    auto doc = f.makeDoc(10,  {11, 12});
    f.put(10, *doc, 1);
    TEST_DO(f.checkAttrs(1, 10, {11, 12}));
    doc = f.makeDoc(20, {21});
    f.update(11, *doc, 1, true);
    TEST_DO(f.checkAttrs(1, 10, {21}));
}

struct StructMapFixture : public StructFixtureBase
{
    using StructFixtureBase::makeDoc;
    const MapDataType _structMapFieldType;
    const Field _structMapField;

    StructMapFixture()
        : StructFixtureBase(),
          _structMapFieldType(*DataType::INT, _structFieldType),
          _structMapField("map", _structMapFieldType, true)
    {
        addAttribute({"map.value.value", AVConfig(AVBasicType::INT32, AVCollectionType::ARRAY)}, createSerialNum);
        addAttribute({"map.key", AVConfig(AVBasicType::INT32, AVCollectionType::ARRAY)}, createSerialNum);
        _type.addField(_structMapField);
    }

    std::unique_ptr<Document>
    makeDoc(int32_t value, const std::map<int32_t, int32_t> &mapValues)
    {
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
        auto valueAttr = _m->getAttribute("value")->getSP();
        auto mapKeyAttr = _m->getAttribute("map.key")->getSP();
        auto mapValueAttr = _m->getAttribute("map.value.value")->getSP();
        EXPECT_EQUAL(expValue, valueAttr->getInt(lid));
        attribute::IntegerContent mapKeys;
        mapKeys.fill(*mapKeyAttr, lid);
        attribute::IntegerContent mapValues;
        mapValues.fill(*mapValueAttr, lid);
        EXPECT_EQUAL(expMap.size(), mapValues.size());
        EXPECT_EQUAL(expMap.size(), mapKeys.size());
        size_t i = 0;
        for (const auto &expMapElem : expMap) {
            EXPECT_EQUAL(expMapElem.first, mapKeys[i]);
            EXPECT_EQUAL(expMapElem.second, mapValues[i]);
            ++i;
        }
    }
};

TEST_F("require that update with doc argument updates struct field attributes (map)", StructMapFixture)
{
    auto doc = f.makeDoc(10,  {{1, 11}, {2, 12}});
    f.put(10, *doc, 1);
    TEST_DO(f.checkAttrs(1, 10, {{1, 11}, {2, 12}}));
    doc = f.makeDoc(20, {{42, 21}});
    f.update(11, *doc, 1, true);
    TEST_DO(f.checkAttrs(1, 10, {{42, 21}}));
}

TEST_MAIN()
{
    vespalib::rmdir(test_dir, true);
    TEST_RUN_ALL();
}
