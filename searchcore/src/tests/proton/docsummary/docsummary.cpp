// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/proton/common/dummydbowner.h>
#include <vespa/config-bucketspaces.h>
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/document/annotation/annotation.h>
#include <vespa/document/annotation/span.h>
#include <vespa/document/annotation/spanlist.h>
#include <vespa/document/annotation/spantree.h>
#include <vespa/document/config/documenttypes_config_fwd.h>
#include <vespa/document/datatype/annotationtype.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/arrayfieldvalue.h>
#include <vespa/document/fieldvalue/bytefieldvalue.h>
#include <vespa/document/fieldvalue/doublefieldvalue.h>
#include <vespa/document/fieldvalue/floatfieldvalue.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/longfieldvalue.h>
#include <vespa/document/fieldvalue/rawfieldvalue.h>
#include <vespa/document/fieldvalue/shortfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>
#include <vespa/document/fieldvalue/weightedsetfieldvalue.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/fixedtyperepo.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/test/value_compare.h>
#include <vespa/eval/eval/value.h>
#include <vespa/searchcore/proton/attribute/attribute_writer.h>
#include <vespa/searchcore/proton/docsummary/docsumcontext.h>
#include <vespa/searchcore/proton/docsummary/documentstoreadapter.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastore.h>
#include <vespa/searchcore/proton/feedoperation/putoperation.h>
#include <vespa/searchcore/proton/matching/querylimiter.h>
#include <vespa/searchcore/proton/metrics/metricswireservice.h>
#include <vespa/searchcore/proton/server/bootstrapconfig.h>
#include <vespa/searchcore/proton/server/documentdb.h>
#include <vespa/searchcore/proton/server/documentdbconfigmanager.h>
#include <vespa/searchcore/proton/server/feedhandler.h>
#include <vespa/searchcore/proton/server/idocumentsubdb.h>
#include <vespa/searchcore/proton/server/memoryconfigstore.h>
#include <vespa/searchcore/proton/server/searchview.h>
#include <vespa/searchcore/proton/server/summaryadapter.h>
#include <vespa/searchcore/proton/test/bucketfactory.h>
#include <vespa/searchcore/proton/test/mock_shared_threading_service.h>
#include <vespa/searchlib/attribute/interlock.h>
#include <vespa/searchlib/engine/docsumapi.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/tensor/tensor_attribute.h>
#include <vespa/searchlib/test/doc_builder.h>
#include <vespa/searchlib/transactionlog/nosyncproxy.h>
#include <vespa/searchlib/transactionlog/translogserver.h>
#include <vespa/searchsummary/docsummary/i_docsum_field_writer_factory.h>
#include <vespa/searchsummary/docsummary/i_docsum_store_document.h>
#include <vespa/searchsummary/docsummary/i_juniper_converter.h>
#include <vespa/searchsummary/docsummary/linguisticsannotation.h>
#include <vespa/vespalib/data/simple_buffer.h>
#include <vespa/vespalib/data/slime/json_format.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/encoding/base64.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/geo/zcurve.h>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/config-summary.h>
#include <filesystem>
#include <regex>

#include <vespa/log/log.h>
LOG_SETUP("docsummary_test");

using config::ConfigGetter;

using namespace cloud::config::filedistribution;
using namespace document;
using namespace search::docsummary;
using namespace search::engine;
using namespace search::index;
using namespace search::transactionlog;
using namespace search;
using namespace std::chrono_literals;

using vespalib::IDestructorCallback;
using document::test::makeBucketSpace;
using search::TuneFileDocumentDB;
using search::index::DummyFileHeaderContext;
using search::linguistics::SPANTREE_NAME;
using search::test::DocBuilder;
using storage::spi::Timestamp;
using vespa::config::search::core::ProtonConfig;
using vespa::config::content::core::BucketspacesConfig;
using vespalib::eval::SimpleValue;
using vespalib::eval::TensorSpec;
using vespalib::eval::ValueType;
using vespalib::GateCallback;
using vespalib::Slime;
using vespalib::geo::ZCurve;
using namespace vespalib::slime;

namespace proton {

class MockDocsumFieldWriterFactory : public search::docsummary::IDocsumFieldWriterFactory
{
public:
    std::unique_ptr<DocsumFieldWriter> create_docsum_field_writer(const vespalib::string&, const vespalib::string&, const vespalib::string&) override {
        return {};
    }

};

class DirMaker
{
public:
    explicit DirMaker(const vespalib::string & dir) :
        _dir(dir)
    {
        std::filesystem::create_directory(std::filesystem::path(dir));
    }
    ~DirMaker() {
        std::filesystem::remove_all(std::filesystem::path(_dir));
    }
private:
    vespalib::string _dir;
};

class BuildContext : public DocBuilder
{
public:
    DirMaker _dmk;
    document::FixedTypeRepo                 _fixed_repo;
    DummyFileHeaderContext _fileHeaderContext;
    vespalib::ThreadStackExecutor _summaryExecutor;
    search::transactionlog::NoSyncProxy _noTlSyncer;
    search::LogDocumentStore _str;
    uint64_t _serialNum;

    explicit BuildContext(AddFieldsType addfields);

    ~BuildContext();

    const document::FixedTypeRepo& get_fixed_repo() { return _fixed_repo; }

    void
    put_document(uint32_t docId, std::unique_ptr<Document> doc)
    {
        _str.write(_serialNum++, docId, *doc);
    }

    StringFieldValue make_annotated_string();
};

BuildContext::BuildContext(AddFieldsType add_fields)
    : DocBuilder(add_fields),
      _dmk("summary"),
      _fixed_repo(get_repo(), get_document_type()),
      _summaryExecutor(4, 128_Ki),
      _noTlSyncer(),
      _str(_summaryExecutor, "summary",
           LogDocumentStore::Config(
                   DocumentStore::Config(),
                   LogDataStore::Config()),
           GrowStrategy(),
           TuneFileSummary(),
           _fileHeaderContext,
           _noTlSyncer,
           nullptr),
      _serialNum(1)
{
}

BuildContext::~BuildContext() = default;

StringFieldValue
BuildContext::make_annotated_string()
{
    auto span_list_up = std::make_unique<SpanList>();
    auto span_list = span_list_up.get();
    auto tree = std::make_unique<SpanTree>(SPANTREE_NAME, std::move(span_list_up));
    tree->annotate(span_list->add(std::make_unique<Span>(0, 3)), *AnnotationType::TERM);
    tree->annotate(span_list->add(std::make_unique<Span>(4, 3)),
                   Annotation(*AnnotationType::TERM, std::make_unique<StringFieldValue>("baz")));
    StringFieldValue value("foo bar");
    StringFieldValue::SpanTrees trees;
    trees.push_back(std::move(tree));
    value.setSpanTrees(trees, _fixed_repo);
    return value;
}

namespace {

const char *
getDocTypeName()
{
    return "searchdocument";
}

vespalib::eval::Value::UP make_tensor(const TensorSpec &spec) {
    return SimpleValue::from_spec(spec);
}

vespalib::string asVstring(vespalib::Memory str) {
    return vespalib::string(str.data, str.size);
}
vespalib::string asVstring(const Inspector &value) {
    return asVstring(value.asString());
}

std::string b64encode(const Inspector &value) {
    vespalib::Memory mem = value.asData();
    std::string str(mem.data, mem.size);
    return vespalib::Base64::encode(str);
}

}  // namespace

class DBContext : public DummyDBOwner
{
public:
    DirMaker _dmk;
    DummyFileHeaderContext        _fileHeaderContext;
    vespalib::ThreadStackExecutor _summaryExecutor;
    MockSharedThreadingService    _shared_service;
    TransLogServer                _tls;
    bool _made_dir;
    matching::QueryLimiter _queryLimiter;
    DummyWireService _dummy;
    ::config::DirSpec _spec;
    DocumentDBConfigHelper _configMgr;
    DocumentDBConfig::DocumenttypesConfigSP _documenttypesConfig;
    const std::shared_ptr<const DocumentTypeRepo> _repo;
    TuneFileDocumentDB::SP _tuneFileDocumentDB;
    HwInfo _hwInfo;
    std::shared_ptr<DocumentDB> _ddb;
    AttributeWriter::UP _aw;
    ISummaryAdapter::SP _sa;

    DBContext(std::shared_ptr<const DocumentTypeRepo> repo, const char *docTypeName)
        : _dmk(docTypeName),
          _fileHeaderContext(),
          _summaryExecutor(8, 128_Ki),
          _shared_service(_summaryExecutor, _summaryExecutor),
          _tls(_shared_service.transport(), "tmp", 9013, ".", _fileHeaderContext),
          _made_dir(std::filesystem::create_directory(std::filesystem::path("tmpdb"))),
          _queryLimiter(),
          _dummy(),
          _spec(TEST_PATH("")),
          _configMgr(_spec, getDocTypeName()),
          _documenttypesConfig(std::make_shared<DocumenttypesConfig>()),
          _repo(std::move(repo)),
          _tuneFileDocumentDB(std::make_shared<TuneFileDocumentDB>()),
          _hwInfo(),
          _ddb(),
          _aw(),
          _sa()
    {
        (void) _made_dir;
        auto b = std::make_shared<BootstrapConfig>(1, _documenttypesConfig, _repo,
                                                   std::make_shared<ProtonConfig>(),
                                                   std::make_shared<FiledistributorrpcConfig>(),
                                                   std::make_shared<BucketspacesConfig>(),
                                                   _tuneFileDocumentDB, _hwInfo);
        _configMgr.forwardConfig(b);
        _configMgr.nextGeneration(_shared_service.transport(), 0ms);
        std::filesystem::create_directory(std::filesystem::path(std::string("tmpdb/") + docTypeName));
        _ddb = DocumentDB::create("tmpdb", _configMgr.getConfig(), "tcp/localhost:9013", _queryLimiter,
                                  DocTypeName(docTypeName), makeBucketSpace(), *b->getProtonConfigSP(), *this,
                                  _shared_service, _tls, _dummy, _fileHeaderContext,
                                  std::make_shared<search::attribute::Interlock>(),
                                  std::make_unique<MemoryConfigStore>(),
                                  std::make_shared<vespalib::ThreadStackExecutor>(16, 128_Ki), _hwInfo),
            _ddb->start();
        _ddb->waitForOnlineState();
        _aw = std::make_unique<AttributeWriter>(_ddb->getReadySubDB()->getAttributeManager());
        _sa = _ddb->getReadySubDB()->getSummaryAdapter();
    }
    ~DBContext() override;

    void
    put(const document::Document &doc, const search::DocumentIdT lid)
    {
        const document::DocumentId &docId = doc.getId();
        typedef DocumentMetaStore::Result PutRes;
        IDocumentMetaStore &dms = _ddb->getReadySubDB()->getDocumentMetaStoreContext().get();
        uint32_t docSize = 1;
        PutRes putRes(dms.put(docId.getGlobalId(), BucketFactory::getBucketId(docId),
                              Timestamp(0u), docSize, lid, 0u));
        LOG_ASSERT(putRes.ok());
        uint64_t serialNum = _ddb->getFeedHandler().inc_serial_num();
        dms.commit(CommitParam(serialNum));
        _aw->put(serialNum, doc, lid, std::shared_ptr<IDestructorCallback>());
        {
            vespalib::Gate gate;
            _aw->forceCommit(serialNum, std::make_shared<GateCallback>(gate));
            gate.await();
        }
        _sa->put(serialNum, lid, doc);
        const GlobalId &gid = docId.getGlobalId();
        BucketId bucketId(gid.convertToBucketId());
        bucketId.setUsedBits(8);
        storage::spi::Timestamp ts(0);
        DbDocumentId dbdId(lid);
        DbDocumentId prevDbdId(0);
        auto op = std::make_unique<PutOperation>(bucketId, ts, std::make_shared<document::Document>(doc));
        op->setSerialNum(serialNum);
        op->setDbDocumentId(dbdId);
        op->setPrevDbDocumentId(prevDbdId);
        vespalib::Gate commitDone;
        _ddb->getWriteService().master().execute(vespalib::makeLambdaTask([this, op = std::move(op), &commitDone]() {
            _ddb->getFeedHandler().appendOperation(*op, std::make_shared<vespalib::GateCallback>(commitDone));
        }));
        commitDone.await();
        SearchView *sv(dynamic_cast<SearchView *>(_ddb->getReadySubDB()->getSearchView().get()));
        if (sv != nullptr) {
            // cf. FeedView::putAttributes()
            DocIdLimit &docIdLimit = sv->getDocIdLimit();
            if (docIdLimit.get() <= lid)
                docIdLimit.set(lid + 1);
        }
    }
};

DBContext::~DBContext()
{
    _sa.reset();
    _aw.reset();
    _ddb.reset();
    std::filesystem::remove_all(std::filesystem::path("tmp"));
    std::filesystem::remove_all(std::filesystem::path("tmpdb"));
}

class Fixture
{
private:
    std::unique_ptr<vespa::config::search::SummaryConfig> _summaryCfg;
    ResultConfig               _resultCfg;

public:
    Fixture();
    ~Fixture();

    const ResultConfig &getResultConfig() const{
        return _resultCfg;
    }
};

class MockJuniperConverter : public IJuniperConverter
{
    vespalib::string _result;
public:
    void convert(vespalib::stringref input, vespalib::slime::Inserter&) override {
        _result = input;
    }
    const vespalib::string& get_result() const noexcept { return _result; }
};

bool
assertString(const std::string & exp, const std::string & fieldName,
             DocumentStoreAdapter &dsa, uint32_t id)
{
    auto res = dsa.get_document(id);
    return EXPECT_EQUAL(exp, res->get_field_value(fieldName)->getAsString());
}

bool
assertAnnotatedString(const std::string & exp, const std::string & fieldName,
                      DocumentStoreAdapter &dsa, uint32_t id)
{
    auto res = dsa.get_document(id);
    MockJuniperConverter converter;
    vespalib::Slime slime;
    vespalib::slime::SlimeInserter inserter(slime);
    res->insert_juniper_field(fieldName, inserter, converter);
    return EXPECT_EQUAL(exp, converter.get_result());
}

void
assertTensor(const vespalib::eval::Value::UP & exp, const std::string & fieldName,
             const DocsumReply & reply, uint32_t id)
{
    const auto & root = reply.root();
    if (exp) {
        EXPECT_TRUE(root["docsums"].valid());
        EXPECT_TRUE(root["docsums"][id].valid());
        EXPECT_TRUE(root["docsums"][id]["docsum"].valid());
        EXPECT_TRUE(root["docsums"][id]["docsum"][fieldName].valid());
        vespalib::Memory data = root["docsums"][id]["docsum"][fieldName].asData();
        vespalib::nbostream x(data.data, data.size);
        auto tensor = SimpleValue::from_stream(x);
        EXPECT_TRUE(tensor.get() != nullptr);
        EXPECT_EQUAL(*exp, *tensor);
    } else {
        EXPECT_FALSE(root["docsums"][id][fieldName].valid());
    }
}

bool assertSlime(const std::string &exp, const DocsumReply &reply) {
    vespalib::Slime expSlime;
    size_t used = JsonFormat::decode(exp, expSlime);
    EXPECT_TRUE(used > 0);
    ASSERT_TRUE(reply.hasResult());
    return (EXPECT_EQUAL(expSlime, reply.slime()));
}

TEST_F("requireThatAdapterHandlesAllFieldTypes", Fixture)
{
    BuildContext bc([](auto& header)
                    {
                        header
                            .addField("a", DataType::T_BYTE)
                            .addField("b", DataType::T_SHORT)
                            .addField("c", DataType::T_INT)
                            .addField("d", DataType::T_LONG)
                            .addField("e", DataType::T_FLOAT)
                            .addField("f", DataType::T_DOUBLE)
                            .addField("g", DataType::T_STRING)
                            .addField("h", DataType::T_STRING)
                            .addField("i", DataType::T_RAW)
                            .addField("j", DataType::T_RAW)
                            .addField("k", DataType::T_STRING)
                            .addField("l", DataType::T_STRING);
                    });

    auto doc = bc.make_document("id:ns:searchdocument::0");
    doc->setValue("a", ByteFieldValue(-1));
    doc->setValue("b", ShortFieldValue(32767));
    doc->setValue("c", IntFieldValue(2147483647));
    doc->setValue("d", LongFieldValue(2147483648));
    doc->setValue("e", FloatFieldValue(1234.56));
    doc->setValue("f", DoubleFieldValue(9876.54));
    doc->setValue("g", StringFieldValue("foo"));
    doc->setValue("h", StringFieldValue("bar"));
    doc->setValue("i", RawFieldValue("baz"));
    doc->setValue("j", RawFieldValue("qux"));
    doc->setValue("k", StringFieldValue("<foo>"));
    doc->setValue("l", StringFieldValue("{foo:10}"));
    bc.put_document(0, std::move(doc));

    DocumentStoreAdapter dsa(bc._str, bc.get_repo());
    auto res = dsa.get_document(0);
    EXPECT_EQUAL(-1,          res->get_field_value("a")->getAsInt());
    EXPECT_EQUAL(32767,       res->get_field_value("b")->getAsInt());
    EXPECT_EQUAL(2147483647,  res->get_field_value("c")->getAsInt());
    EXPECT_EQUAL(INT64_C(2147483648), res->get_field_value("d")->getAsLong());
    EXPECT_APPROX(1234.56,    res->get_field_value("e")->getAsFloat(), 10e-5);
    EXPECT_APPROX(9876.54,    res->get_field_value("f")->getAsDouble(), 10e-5);
    EXPECT_EQUAL("foo",       res->get_field_value("g")->getAsString());
    EXPECT_EQUAL("bar",       res->get_field_value("h")->getAsString());
    EXPECT_EQUAL("baz",       res->get_field_value("i")->getAsString());
    EXPECT_EQUAL("qux",       res->get_field_value("j")->getAsString());
    EXPECT_EQUAL("<foo>",     res->get_field_value("k")->getAsString());
    EXPECT_EQUAL("{foo:10}",  res->get_field_value("l")->getAsString());
}

TEST_F("requireThatAdapterHandlesMultipleDocuments", Fixture)
{
    BuildContext bc([](auto& header) { header.addField("a", DataType::T_INT); });
    auto doc = bc.make_document("id:ns:searchdocument::0");
    doc->setValue("a", IntFieldValue(1000));
    bc.put_document(0, std::move(doc));
    doc = bc.make_document("id:ns:searchdocument::1");
    doc->setValue("a", IntFieldValue(2000));
    bc.put_document(1, std::move(doc));

    DocumentStoreAdapter dsa(bc._str, bc.get_repo());
    { // doc 0
        auto res = dsa.get_document(0);
        EXPECT_EQUAL(1000, res->get_field_value("a")->getAsInt());
    }
    { // doc 1
        auto res = dsa.get_document(1);
        EXPECT_EQUAL(2000, res->get_field_value("a")->getAsInt());
    }
    { // doc 2
        auto res = dsa.get_document(2);
        EXPECT_TRUE(!res);
    }
    { // doc 0 (again)
        auto res = dsa.get_document(0);
        EXPECT_EQUAL(1000, res->get_field_value("a")->getAsInt());
    }
    EXPECT_EQUAL(0u, bc._str.lastSyncToken());
    uint64_t flushToken = bc._str.initFlush(bc._serialNum - 1);
    bc._str.flush(flushToken);
}

TEST_F("requireThatAdapterHandlesDocumentIdField", Fixture)
{
    BuildContext bc([](auto&) noexcept {});
    auto doc = bc.make_document("id:ns:searchdocument::0");
    bc.put_document(0, std::move(doc));
    DocumentStoreAdapter dsa(bc._str, bc.get_repo());
    auto res = dsa.get_document(0);
    vespalib::Slime slime;
    vespalib::slime::SlimeInserter inserter(slime);
    res->insert_document_id(inserter);
    EXPECT_EQUAL("id:ns:searchdocument::0", slime.get().asString().make_string());
}

GlobalId gid1 = DocumentId("id:ns:searchdocument::1").getGlobalId(); // lid 1
GlobalId gid2 = DocumentId("id:ns:searchdocument::2").getGlobalId(); // lid 2
GlobalId gid3 = DocumentId("id:ns:searchdocument::3").getGlobalId(); // lid 3
GlobalId gid4 = DocumentId("id:ns:searchdocument::4").getGlobalId(); // lid 4
GlobalId gid9 = DocumentId("id:ns:searchdocument::9").getGlobalId(); // not existing

TEST("requireThatDocsumRequestIsProcessed")
{
    BuildContext bc([](auto& header) { header.addField("a", DataType::T_INT); });
    DBContext dc(bc.get_repo_sp(), getDocTypeName());
    auto doc = bc.make_document("id:ns:searchdocument::1");
    doc->setValue("a", IntFieldValue(10));
    dc.put(*doc, 1);
    doc = bc.make_document("id:ns:searchdocument::2");
    doc->setValue("a", IntFieldValue(20));
    dc.put(*doc, 2);
    doc = bc.make_document("id:ns:searchdocument::3");
    doc->setValue("a", IntFieldValue(30));
    dc.put(*doc, 3);
    doc = bc.make_document("id:ns:searchdocument::4");
    doc->setValue("a", IntFieldValue(40));
    dc.put(*doc, 4);
    doc = bc.make_document("id:ns:searchdocument::5");
    doc->setValue("a", IntFieldValue(50));
    dc.put(*doc, 5);

    DocsumRequest req;
    req.resultClassName = "class1";
    req.hits.emplace_back(gid2);
    req.hits.emplace_back(gid4);
    req.hits.emplace_back(gid9);
    DocsumReply::UP rep = dc._ddb->getDocsums(req);
    EXPECT_TRUE(assertSlime("{docsums:[ {docsum:{a:20}}, {docsum:{a:40}}, {} ]}", *rep));
}

TEST("requireThatRewritersAreUsed")
{
    BuildContext bc([](auto& header)
                    { header.addField("aa", DataType::T_INT)
                            .addField("ab", DataType::T_INT); });
    DBContext dc(bc.get_repo_sp(), getDocTypeName());
    auto doc = bc.make_document("id:ns:searchdocument::1");
    doc->setValue("aa", IntFieldValue(10));
    doc->setValue("ab", IntFieldValue(20));
    dc.put(*doc, 1);

    DocsumRequest req;
    req.resultClassName = "class2";
    req.hits.emplace_back(gid1);
    DocsumReply::UP rep = dc._ddb->getDocsums(req);
    EXPECT_TRUE(assertSlime("{docsums:[ {docsum:{aa:20}} ]}", *rep));
}

TEST("requireThatSummariesTimeout")
{
    BuildContext bc([](auto& header)
                    { header.addField("aa", DataType::T_INT)
                            .addField("ab", DataType::T_INT); });
    DBContext dc(bc.get_repo_sp(), getDocTypeName());
    auto doc = bc.make_document("id:ns:searchdocument::1");
    doc->setValue("aa", IntFieldValue(10));
    doc->setValue("ab", IntFieldValue(20));
    dc.put(*doc, 1);

    DocsumRequest req;
    req.setTimeout(vespalib::duration::zero());
    EXPECT_TRUE(req.expired());
    req.resultClassName = "class2";
    req.hits.emplace_back(gid1);
    DocsumReply::UP rep = dc._ddb->getDocsums(req);
    const auto & root = rep->root();
    const auto & field = root["errors"];
    EXPECT_TRUE(field.valid());
    EXPECT_EQUAL(field[0]["type"].asString(), "timeout");
    auto bufstring = field[0]["message"].asString();
    EXPECT_TRUE(std::regex_search(bufstring.data, bufstring.data + bufstring.size, std::regex("Timed out 1 summaries with -[0-9]+us left.")));
}

void verifyFieldListHonoured(DocsumRequest::FieldList fields, const std::string & json) {
    BuildContext bc([](auto& header)
                    { header.addField("ba", DataType::T_INT)
                            .addField("bb", DataType::T_FLOAT); });
    DBContext dc(bc.get_repo_sp(), getDocTypeName());
    auto doc = bc.make_document("id:ns:searchdocument::1");
    doc->setValue("ba", IntFieldValue(10));
    doc->setValue("bb", FloatFieldValue(10.1250));
    dc.put(*doc, 1);
    DocsumRequest req;
    req.resultClassName = "class6";
    req.hits.emplace_back(gid1);
    req.setFields(fields);
    DocsumReply::UP rep = dc._ddb->getDocsums(req);
    EXPECT_TRUE(assertSlime(json, *rep));
}

TEST("requireThatFieldListIsHonoured")
{
    verifyFieldListHonoured({}, "{docsums:[ {docsum:{ba:10,bb:10.1250}} ]}");
    verifyFieldListHonoured({"ba","bb"}, "{docsums:[ {docsum:{ba:10,bb:10.1250}} ]}");
    verifyFieldListHonoured({"ba"}, "{docsums:[ {docsum:{ba:10}} ]}");
    verifyFieldListHonoured({"bb"}, "{docsums:[ {docsum:{bb:10.1250}} ]}");
    verifyFieldListHonoured({"unknown"}, "{docsums:[ {docsum:{}} ]}");
    verifyFieldListHonoured({"ba", "unknown"}, "{docsums:[ {docsum:{ba:10}} ]}");
}

TEST("requireThatAttributesAreUsed")
{
    BuildContext bc([](auto& header)
                    { using namespace document::config_builder;
                        header.addField("ba", DataType::T_INT)
                            .addField("bb", DataType::T_FLOAT)
                            .addField("bc", DataType::T_STRING)
                            .addField("bd", Array(DataType::T_INT))
                            .addField("be", Array(DataType::T_FLOAT))
                            .addField("bf", Array(DataType::T_STRING))
                            .addField("bg", Wset(DataType::T_INT))
                            .addField("bh", Wset(DataType::T_FLOAT))
                            .addField("bi", Wset(DataType::T_STRING))
                            .addTensorField("bj", "tensor(x{},y{})"); });
    DBContext dc(bc.get_repo_sp(), getDocTypeName());
    auto doc = bc.make_document("id:ns:searchdocument::1");
    dc.put(*doc, 1); // empty doc
    doc = bc.make_document("id:ns:searchdocument::2");
    doc->setValue("ba", IntFieldValue(10));
    doc->setValue("bb", FloatFieldValue(10.1250));
    doc->setValue("bc", StringFieldValue("foo"));
    auto int_array = bc.make_array("bd");
    int_array.add(IntFieldValue(20));
    int_array.add(IntFieldValue(30));
    doc->setValue("bd", int_array);
    auto float_array = bc.make_array("be");
    float_array.add(FloatFieldValue(20.25000));
    float_array.add(FloatFieldValue(30.31250));
    doc->setValue("be", float_array);
    auto string_array = bc.make_array("bf");
    string_array.add(StringFieldValue("bar"));
    string_array.add(StringFieldValue("baz"));
    doc->setValue("bf", string_array);
    auto int_wset = bc.make_wset("bg");
    int_wset.add(IntFieldValue(40), 2);
    int_wset.add(IntFieldValue(50), 3);
    doc->setValue("bg", int_wset);
    auto float_wset = bc.make_wset("bh");
    float_wset.add(FloatFieldValue(40.4375), 4);
    float_wset.add(FloatFieldValue(50.5625), 5);
    doc->setValue("bh", float_wset);
    auto string_wset = bc.make_wset("bi");
    string_wset.add(StringFieldValue("quux"), 7);
    string_wset.add(StringFieldValue("qux"), 6);
    doc->setValue("bi", string_wset);
    TensorDataType tensor_data_type(ValueType::from_spec("tensor(x{},y{})"));
    TensorFieldValue tensor(tensor_data_type);
    tensor = make_tensor(TensorSpec("tensor(x{},y{})")
                         .add({{"x", "f"}, {"y", "g"}}, 3));
    doc->setValue("bj", tensor);
    dc.put(*doc, 2);
    doc = bc.make_document("id:ns:searchdocument::3");
    dc.put(*doc, 3); // empty doc

    DocsumRequest req;
    req.resultClassName = "class3";
    req.hits.emplace_back(gid2);
    req.hits.emplace_back(gid3);
    DocsumReply::UP rep = dc._ddb->getDocsums(req);

    EXPECT_TRUE(assertSlime("{docsums:[ {docsum:{"
                            "ba:10,bb:10.1250,"
                            "bc:'foo',"
                            "bd:[20,30],"
                            "be:[20.2500,30.3125],"
                            "bf:['bar','baz'],"
                            "bg:[{item:40,weight:2},{item:50,weight:3}],"
                            "bh:[{item:40.4375,weight:4},{item:50.5625,weight:5}],"
                            "bi:[{item:'quux',weight:7},{item:'qux',weight:6}],"
                            "bj:x01020178017901016601674008000000000000}},"
                            "{docsum:{}}]}", *rep));

    TEST_DO(assertTensor(make_tensor(TensorSpec("tensor(x{},y{})")
                                     .add({{"x", "f"}, {"y", "g"}}, 3)),
                         "bj", *rep, 0));

    proton::IAttributeManager::SP attributeManager = dc._ddb->getReadySubDB()->getAttributeManager();
    vespalib::ISequencedTaskExecutor &attributeFieldWriter = attributeManager->getAttributeFieldWriter();
    search::AttributeVector *bjAttr = attributeManager->getWritableAttribute("bj");
    auto bjTensorAttr = dynamic_cast<search::tensor::TensorAttribute *>(bjAttr);

    vespalib::Gate gate;
    {
        auto on_write_done = std::make_shared<GateCallback>(gate);
        attributeFieldWriter.execute(attributeFieldWriter.getExecutorIdFromName(bjAttr->getNamePrefix()),
                                     [&, on_write_done]() {
                                         (void) on_write_done;
                                         bjTensorAttr->setTensor(3, *make_tensor(TensorSpec("tensor(x{},y{})")
                                                                             .add({{"x", "a"}, {"y", "b"}}, 4)));
                                         bjTensorAttr->commit();
                                 });
    }
    gate.await();

    DocsumReply::UP rep2 = dc._ddb->getDocsums(req);
    TEST_DO(assertTensor(make_tensor(TensorSpec("tensor(x{},y{})")
                                     .add({{"x", "a"}, {"y", "b"}}, 4)),
                         "bj", *rep2, 1));

    DocsumRequest req3;
    req3.resultClassName = "class3";
    req3.hits.emplace_back(gid3);
    DocsumReply::UP rep3 = dc._ddb->getDocsums(req3);
    EXPECT_TRUE(assertSlime("{docsums:[{docsum:{bj:x01020178017901016101624010000000000000}}]}", *rep3));
}

TEST("requireThatSummaryAdapterHandlesPutAndRemove")
{
    BuildContext bc([](auto& header) { header.addField("f1", DataType::T_STRING); });
    DBContext dc(bc.get_repo_sp(), getDocTypeName());
    auto exp = bc.make_document("id:ns:searchdocument::1");
    exp->setValue("f1", StringFieldValue("foo"));
    dc._sa->put(1, 1, *exp);
    IDocumentStore & store = dc._ddb->getReadySubDB()->getSummaryManager()->getBackingStore();
    Document::UP act = store.read(1, bc.get_repo());
    EXPECT_TRUE(act.get() != nullptr);
    EXPECT_EQUAL(exp->getType(), act->getType());
    EXPECT_EQUAL("foo", act->getValue("f1")->toString());
    dc._sa->remove(2, 1);
    EXPECT_TRUE(store.read(1, bc.get_repo()).get() == nullptr);
}

const std::string TERM_ORIG = "\357\277\271";
const std::string TERM_INDEX = "\357\277\272";
const std::string TERM_END = "\357\277\273";
const std::string TERM_SEP = "\037";
const std::string TERM_EMPTY;
namespace
{
const std::string empty;
}

TEST_F("requireThatAnnotationsAreUsed", Fixture)
{
    BuildContext bc([](auto& header)
                    { header.addField("g", DataType::T_STRING)
                            .addField("dynamicstring", DataType::T_STRING); });
    DBContext dc(bc.get_repo_sp(), getDocTypeName());
    auto exp = bc.make_document("id:ns:searchdocument::0");
    exp->setValue("g", bc.make_annotated_string());
    exp->setValue("dynamicstring", bc.make_annotated_string());
    dc._sa->put(1, 1, *exp);

    IDocumentStore & store = dc._ddb->getReadySubDB()->getSummaryManager()->getBackingStore();
    Document::UP act = store.read(1, bc.get_repo());
    EXPECT_TRUE(act.get() != nullptr);
    EXPECT_EQUAL(exp->getType(), act->getType());
    EXPECT_EQUAL("foo bar", act->getValue("g")->getAsString());
    EXPECT_EQUAL("foo bar", act->getValue("dynamicstring")->getAsString());

    DocumentStoreAdapter dsa(store, bc.get_repo());
    EXPECT_TRUE(assertString("foo bar", "g", dsa, 1));
    EXPECT_TRUE(assertAnnotatedString(TERM_EMPTY + "foo" + TERM_SEP +
                                      " " + TERM_SEP +
                                      TERM_ORIG + "bar" + TERM_INDEX + "baz" + TERM_END +
                                      TERM_SEP,
                                      "dynamicstring", dsa, 1));
}

TEST_F("requireThatUrisAreUsed", Fixture)
{
    BuildContext bc([](auto& header)
                    { using namespace document::config_builder;
                        header.addField("urisingle", DataType::T_URI)
                            .addField("uriarray", Array(DataType::T_URI))
                            .addField("uriwset", Wset(DataType::T_URI)); });
    DBContext dc(bc.get_repo_sp(), getDocTypeName());
    auto exp = bc.make_document("id:ns:searchdocument::0");
    exp->setValue("urisingle", StringFieldValue("http://www.example.com:81/fluke?ab=2#4"));
    auto uri_array = bc.make_array("uriarray");
    uri_array.add(StringFieldValue("http://www.example.com:82/fluke?ab=2#8"));
    uri_array.add(StringFieldValue("http://www.flickr.com:82/fluke?ab=2#9"));
    exp->setValue("uriarray", uri_array);
    auto uri_wset = bc.make_wset("uriwset");
    uri_wset.add(StringFieldValue("http://www.example.com:83/fluke?ab=2#12"), 4);
    uri_wset.add(StringFieldValue("http://www.flickr.com:85/fluke?ab=2#13"), 7);
    exp->setValue("uriwset", uri_wset);
    dc._sa->put(1, 1, *exp);

    IDocumentStore & store =
        dc._ddb->getReadySubDB()->getSummaryManager()->getBackingStore();
    Document::UP act = store.read(1, bc.get_repo());
    EXPECT_TRUE(act.get() != nullptr);
    EXPECT_EQUAL(exp->getType(), act->getType());

    DocumentStoreAdapter dsa(store, bc.get_repo());
    auto res = dsa.get_document(1);
    {
        vespalib::Slime slime;
        vespalib::slime::SlimeInserter inserter(slime);
        res->insert_summary_field("urisingle", inserter);
        EXPECT_EQUAL("http://www.example.com:81/fluke?ab=2#4", asVstring(slime.get()));
    }
    {
        vespalib::Slime slime;
        vespalib::slime::SlimeInserter inserter(slime);
        res->insert_summary_field("uriarray", inserter);
        EXPECT_TRUE(slime.get().valid());
        EXPECT_EQUAL("http://www.example.com:82/fluke?ab=2#8",  asVstring(slime.get()[0]));
        EXPECT_EQUAL("http://www.flickr.com:82/fluke?ab=2#9", asVstring(slime.get()[1]));
    }
    {
        vespalib::Slime slime;
        vespalib::slime::SlimeInserter inserter(slime);
        res->insert_summary_field("uriwset", inserter);
        EXPECT_TRUE(slime.get().valid());
        EXPECT_EQUAL(4L, slime.get()[0]["weight"].asLong());
        EXPECT_EQUAL(7L, slime.get()[1]["weight"].asLong());
        vespalib::string arr0s = asVstring(slime.get()[0]["item"]);
        vespalib::string arr1s = asVstring(slime.get()[1]["item"]);
        EXPECT_EQUAL("http://www.example.com:83/fluke?ab=2#12", arr0s);
        EXPECT_EQUAL("http://www.flickr.com:85/fluke?ab=2#13", arr1s);
    }
}

TEST("requireThatPositionsAreUsed")
{
    BuildContext bc([](auto& header)
                    { using namespace document::config_builder;
                        header.addField("sp2", DataType::T_LONG)
                            .addField("ap2", Array(DataType::T_LONG))
                            .addField("wp2", Wset(DataType::T_LONG)); });
    DBContext dc(bc.get_repo_sp(), getDocTypeName());
    auto exp = bc.make_document("id:ns:searchdocument::1");
    exp->setValue("sp2", LongFieldValue(ZCurve::encode(1002, 1003)));
    auto pos_array = bc.make_array("ap2");
    pos_array.add(LongFieldValue(ZCurve::encode(1006, 1007)));
    pos_array.add(LongFieldValue(ZCurve::encode(1008, 1009)));
    exp->setValue("ap2", pos_array);
    auto pos_wset = bc.make_wset("wp2");
    pos_wset.add(LongFieldValue(ZCurve::encode(1012, 1013)), 43);
    pos_wset.add(LongFieldValue(ZCurve::encode(1014, 1015)), 44);
    exp->setValue("wp2", pos_wset);
    dc.put(*exp, 1);

    IDocumentStore & store = dc._ddb->getReadySubDB()->getSummaryManager()->getBackingStore();
    Document::UP act = store.read(1, bc.get_repo());
    EXPECT_TRUE(act);
    EXPECT_EQUAL(exp->getType(), act->getType());

    DocsumRequest req;
    req.resultClassName = "class5";
    req.hits.emplace_back(gid1);
    DocsumReply::UP rep = dc._ddb->getDocsums(req);
    EXPECT_TRUE(assertSlime("{docsums:["
                            "{docsum:{sp2:1047758"
                            ",sp2x:{x:1002, y:1003, latlong:'N0.001003;E0.001002'}"
                            ",ap2:[1047806,1048322]"
                            ",ap2x:[{x:1006, y:1007, latlong:'N0.001007;E0.001006'},"
                            "{x:1008, y:1009, latlong:'N0.001009;E0.001008'}]"
                            ",wp2:[{item:1048370,weight:43},{item:1048382,weight:44}]"
                            ",wp2x:[{ x:1012, y:1013, latlong:'N0.001013;E0.001012'},"
                            "{ x:1014, y:1015, latlong:'N0.001015;E0.001014'}]}"
                            "}]}", *rep));
}

TEST_F("requireThatRawFieldsWorks", Fixture)
{
    BuildContext bc([](auto& header)
                    { using namespace document::config_builder;
                        header.addField("i", DataType::T_RAW)
                            .addField("araw", Array(DataType::T_RAW))
                            .addField("wraw", Wset(DataType::T_RAW)); });
    std::vector<char> binaryBlob;
    binaryBlob.push_back('\0');
    binaryBlob.push_back('\2');
    binaryBlob.push_back('\1');
    std::string raw1s("Single Raw Element");
    std::string raw1a0("Array Raw Element 0");
    std::string raw1a1("Array Raw Element  1");
    std::string raw1w0("Weighted Set Raw Element 0");
    std::string raw1w1("Weighted Set Raw Element  1");
    raw1s += std::string(&binaryBlob[0],
                         &binaryBlob[0] + binaryBlob.size());
    raw1a0 += std::string(&binaryBlob[0],
                          &binaryBlob[0] + binaryBlob.size());
    raw1a1 += std::string(&binaryBlob[0],
                          &binaryBlob[0] + binaryBlob.size());
    raw1w0 += std::string(&binaryBlob[0],
                          &binaryBlob[0] + binaryBlob.size());
    raw1w1 += std::string(&binaryBlob[0],
                          &binaryBlob[0] + binaryBlob.size());

    DBContext dc(bc.get_repo_sp(), getDocTypeName());
    auto exp = bc.make_document("id:ns:searchdocument::0");
    exp->setValue("i", RawFieldValue(raw1s));
    auto raw_array = bc.make_array("araw");
    raw_array.add(RawFieldValue(raw1a0));
    raw_array.add(RawFieldValue(raw1a1));
    exp->setValue("araw", raw_array);
    auto raw_wset = bc.make_wset("wraw");
    raw_wset.add(RawFieldValue(raw1w1), 46);
    raw_wset.add(RawFieldValue(raw1w0), 45);
    exp->setValue("wraw", raw_wset);
    dc._sa->put(1, 1, *exp);

    IDocumentStore & store = dc._ddb->getReadySubDB()->getSummaryManager()->getBackingStore();
    Document::UP act = store.read(1, bc.get_repo());
    EXPECT_TRUE(act.get() != nullptr);
    EXPECT_EQUAL(exp->getType(), act->getType());

    DocumentStoreAdapter dsa(store, bc.get_repo());

    ASSERT_TRUE(assertString(raw1s, "i", dsa, 1));

    auto res = dsa.get_document(1);
    {
        vespalib::Slime slime;
        vespalib::slime::SlimeInserter inserter(slime);
        res->insert_summary_field("araw", inserter);
        EXPECT_TRUE(slime.get().valid());
        EXPECT_EQUAL(vespalib::Base64::encode(raw1a0), b64encode(slime.get()[0]));
        EXPECT_EQUAL(vespalib::Base64::encode(raw1a1), b64encode(slime.get()[1]));
    }
    {
        vespalib::Slime slime;
        vespalib::slime::SlimeInserter inserter(slime);
        res->insert_summary_field("wraw", inserter);
        EXPECT_TRUE(slime.get().valid());
        EXPECT_EQUAL(46L, slime.get()[0]["weight"].asLong());
        EXPECT_EQUAL(45L, slime.get()[1]["weight"].asLong());
        std::string arr0s = b64encode(slime.get()[0]["item"]);
        std::string arr1s = b64encode(slime.get()[1]["item"]);
        EXPECT_EQUAL(vespalib::Base64::encode(raw1w1), arr0s);
        EXPECT_EQUAL(vespalib::Base64::encode(raw1w0), arr1s);
    }
}

Fixture::Fixture()
    : _summaryCfg(),
      _resultCfg()
{
    std::string cfgId("summary");
    _summaryCfg = ConfigGetter<vespa::config::search::SummaryConfig>::getConfig(
        cfgId, ::config::FileSpec(TEST_PATH("summary.cfg")));
    auto docsum_field_writer_factory = std::make_unique<MockDocsumFieldWriterFactory>();
    _resultCfg.readConfig(*_summaryCfg, cfgId.c_str(), *docsum_field_writer_factory);
}

Fixture::~Fixture() = default;

}

TEST_MAIN() { TEST_RUN_ALL(); }
