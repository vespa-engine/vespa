// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/proton/common/dummydbowner.h>
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/test/value_compare.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/searchcore/proton/attribute/attribute_writer.h>
#include <vespa/searchcore/proton/test/bucketfactory.h>
#include <vespa/searchcore/proton/docsummary/docsumcontext.h>
#include <vespa/searchcore/proton/docsummary/documentstoreadapter.h>
#include <vespa/searchcore/proton/docsummary/summarymanager.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastore.h>
#include <vespa/searchcore/proton/feedoperation/putoperation.h>
#include <vespa/searchcore/proton/metrics/metricswireservice.h>
#include <vespa/searchcore/proton/server/bootstrapconfig.h>
#include <vespa/searchcore/proton/server/documentdb.h>
#include <vespa/searchcore/proton/server/feedhandler.h>
#include <vespa/searchcore/proton/server/documentdbconfigmanager.h>
#include <vespa/searchcore/proton/server/idocumentsubdb.h>
#include <vespa/searchcore/proton/server/memoryconfigstore.h>
#include <vespa/searchcore/proton/server/searchview.h>
#include <vespa/searchcore/proton/server/summaryadapter.h>
#include <vespa/searchcore/proton/matching/querylimiter.h>
#include <vespa/searchlib/common/gatecallback.h>
#include <vespa/searchlib/engine/docsumapi.h>
#include <vespa/searchlib/index/docbuilder.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/tensor/tensor_attribute.h>
#include <vespa/searchlib/transactionlog/nosyncproxy.h>
#include <vespa/searchlib/transactionlog/translogserver.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/simple_buffer.h>
#include <vespa/vespalib/encoding/base64.h>
#include <vespa/config-bucketspaces.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <regex>

#include <vespa/log/log.h>
LOG_SETUP("docsummary_test");

using namespace cloud::config::filedistribution;
using namespace document;
using namespace search::docsummary;
using namespace search::engine;
using namespace search::index;
using namespace search::transactionlog;
using namespace search;
using namespace std::chrono_literals;

using document::DocumenttypesConfig;
using document::test::makeBucketSpace;
using search::TuneFileDocumentDB;
using search::index::DummyFileHeaderContext;
using search::index::schema::CollectionType;
using storage::spi::Timestamp;
using vespa::config::search::core::ProtonConfig;
using vespa::config::content::core::BucketspacesConfig;
using vespalib::eval::TensorSpec;
using vespalib::eval::SimpleValue;
using namespace vespalib::slime;

typedef std::unique_ptr<GeneralResult> GeneralResultPtr;

namespace proton {

class DirMaker
{
public:
    DirMaker(const vespalib::string & dir) :
        _dir(dir)
    {
        FastOS_File::MakeDirectory(dir.c_str());
    }
    ~DirMaker()
    {
        FastOS_File::EmptyAndRemoveDirectory(_dir.c_str());
    }
private:
    vespalib::string _dir;
};

class BuildContext
{
public:
    DirMaker _dmk;
    DocBuilder _bld;
    std::shared_ptr<const DocumentTypeRepo> _repo;
    DummyFileHeaderContext _fileHeaderContext;
    vespalib::ThreadStackExecutor _summaryExecutor;
    search::transactionlog::NoSyncProxy _noTlSyncer;
    search::LogDocumentStore _str;
    uint64_t _serialNum;

    BuildContext(const Schema &schema)
        : _dmk("summary"),
          _bld(schema),
          _repo(std::make_shared<DocumentTypeRepo>(_bld.getDocumentType())),
          _summaryExecutor(4, 128 * 1024),
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

    ~BuildContext()
    {
    }

    void
    endDocument(uint32_t docId)
    {
        Document::UP doc = _bld.endDocument();
        _str.write(_serialNum++, docId, *doc);
    }

    FieldCacheRepo::UP createFieldCacheRepo(const ResultConfig &resConfig) const {
        return std::make_unique<FieldCacheRepo>(resConfig, _bld.getDocumentType());
    }
};


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

void decode(const ResEntry *entry, vespalib::Slime &slime) {
    vespalib::Memory mem(entry->_dataval, entry->_datalen);
    size_t decodeRes = BinaryFormat::decode(mem, slime);
    ASSERT_EQUAL(decodeRes, mem.size);
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
    DummyFileHeaderContext _fileHeaderContext;
    TransLogServer _tls;
    vespalib::ThreadStackExecutor _summaryExecutor;
    bool _mkdirOk;
    matching::QueryLimiter _queryLimiter;
    vespalib::Clock _clock;
    DummyWireService _dummy;
    config::DirSpec _spec;
    DocumentDBConfigHelper _configMgr;
    DocumentDBConfig::DocumenttypesConfigSP _documenttypesConfig;
    const std::shared_ptr<const DocumentTypeRepo> _repo;
    TuneFileDocumentDB::SP _tuneFileDocumentDB;
    HwInfo _hwInfo;
    std::unique_ptr<DocumentDB> _ddb;
    AttributeWriter::UP _aw;
    ISummaryAdapter::SP _sa;

    DBContext(const std::shared_ptr<const DocumentTypeRepo> &repo, const char *docTypeName)
        : _dmk(docTypeName),
          _fileHeaderContext(),
          _tls("tmp", 9013, ".", _fileHeaderContext),
          _summaryExecutor(8, 128*1024),
          _mkdirOk(FastOS_File::MakeDirectory("tmpdb")),
          _queryLimiter(),
          _clock(),
          _dummy(),
          _spec(TEST_PATH("")),
          _configMgr(_spec, getDocTypeName()),
          _documenttypesConfig(std::make_shared<DocumenttypesConfig>()),
          _repo(repo),
          _tuneFileDocumentDB(std::make_shared<TuneFileDocumentDB>()),
          _hwInfo(),
          _ddb(),
          _aw(),
          _sa()
    {
        assert(_mkdirOk);
        auto b = std::make_shared<BootstrapConfig>(1, _documenttypesConfig, _repo,
                                                   std::make_shared<ProtonConfig>(),
                                                   std::make_shared<FiledistributorrpcConfig>(),
                                                   std::make_shared<BucketspacesConfig>(),
                                                   _tuneFileDocumentDB, _hwInfo);
        _configMgr.forwardConfig(b);
        _configMgr.nextGeneration(0ms);
        if (! FastOS_File::MakeDirectory((std::string("tmpdb/") + docTypeName).c_str())) {
            LOG_ABORT("should not be reached");
        }
        _ddb = std::make_unique<DocumentDB>("tmpdb", _configMgr.getConfig(), "tcp/localhost:9013", _queryLimiter, _clock,
                                            DocTypeName(docTypeName), makeBucketSpace(), *b->getProtonConfigSP(), *this,
                                            _summaryExecutor, _summaryExecutor, _tls, _dummy, _fileHeaderContext,
                                            std::make_unique<MemoryConfigStore>(),
                                            std::make_shared<vespalib::ThreadStackExecutor>(16, 128 * 1024), _hwInfo),
        _ddb->start();
        _ddb->waitForOnlineState();
        _aw = std::make_unique<AttributeWriter>(_ddb->getReadySubDB()->getAttributeManager());
        _sa = _ddb->getReadySubDB()->getSummaryAdapter();
    }
    ~DBContext()
    {
        _sa.reset();
        _aw.reset();
        _ddb.reset();
        FastOS_File::EmptyAndRemoveDirectory("tmp");
        FastOS_File::EmptyAndRemoveDirectory("tmpdb");
    }

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
        _aw->put(serialNum, doc, lid, std::shared_ptr<IDestructorCallback>());
        _aw->forceCommit(serialNum, std::shared_ptr<IDestructorCallback>());
        _ddb->getReadySubDB()->getAttributeManager()->getAttributeFieldWriter().sync();
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
            _ddb->getFeedHandler().appendOperation(*op, std::make_shared<search::GateCallback>(commitDone));
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

class Fixture
{
private:
    std::unique_ptr<vespa::config::search::SummaryConfig> _summaryCfg;
    ResultConfig               _resultCfg;
    std::set<vespalib::string> _markupFields;

public:
    Fixture();
    ~Fixture();

    const ResultConfig &getResultConfig() const{
        return _resultCfg;
    }

    const std::set<vespalib::string> &getMarkupFields() const{
        return _markupFields;
    }
};

GeneralResultPtr
getResult(DocumentStoreAdapter & dsa, uint32_t docId)
{
    DocsumStoreValue docsum = dsa.getMappedDocsum(docId);
    ASSERT_TRUE(docsum.pt() != nullptr);
    auto retval = std::make_unique<GeneralResult>(dsa.getResultClass());
    // skip the 4 byte class id
    ASSERT_TRUE(retval->unpack(docsum.pt() + 4, docsum.len() - 4));
    return retval;
}

bool
assertString(const std::string & exp, const std::string & fieldName,
                   DocumentStoreAdapter &dsa, uint32_t id)
{
    GeneralResultPtr res = getResult(dsa, id);
    return EXPECT_EQUAL(exp, std::string(res->GetEntry(fieldName.c_str())->_stringval,
                                         res->GetEntry(fieldName.c_str())->_stringlen));
}

void
assertTensor(const vespalib::eval::Value::UP & exp, const std::string & fieldName,
                   const DocsumReply & reply, uint32_t id, uint32_t)
{
    const DocsumReply::Docsum & docsum = reply.docsums[id];
    uint32_t classId;
    ASSERT_LESS_EQUAL(sizeof(classId), docsum.data.size());
    memcpy(&classId, docsum.data.c_str(), sizeof(classId));
    ASSERT_EQUAL(::search::docsummary::SLIME_MAGIC_ID, classId);
    vespalib::Slime slime;
    vespalib::Memory serialized(docsum.data.c_str() + sizeof(classId),
                                docsum.data.size() - sizeof(classId));
    size_t decodeRes = BinaryFormat::decode(serialized, slime);
    ASSERT_EQUAL(decodeRes, serialized.size);

    EXPECT_EQUAL(exp.get() != nullptr, slime.get()[fieldName].valid());
    vespalib::Memory data = slime.get()[fieldName].asData();
    EXPECT_EQUAL(exp.get() == nullptr, data.size == 0u);
    if (exp) {
        vespalib::nbostream x(data.data, data.size);
        auto tensor = SimpleValue::from_stream(x);
        EXPECT_TRUE(tensor.get() != nullptr);
        EXPECT_EQUAL(*exp, *tensor);
    }
}

vespalib::Slime
getSlime(const DocsumReply &reply, uint32_t id, bool relaxed)
{
    const DocsumReply::Docsum & docsum = reply.docsums[id];
    uint32_t classId;
    ASSERT_LESS_EQUAL(sizeof(classId), docsum.data.size());
    memcpy(&classId, docsum.data.c_str(), sizeof(classId));
    ASSERT_EQUAL(search::docsummary::SLIME_MAGIC_ID, classId);
    vespalib::Slime slime;
    vespalib::Memory serialized(docsum.data.c_str() + sizeof(classId),
                                docsum.data.size() - sizeof(classId));
    size_t decodeRes = BinaryFormat::decode(serialized, slime);
    ASSERT_EQUAL(decodeRes, serialized.size);
    if (relaxed) {
        vespalib::SimpleBuffer buf;
        JsonFormat::encode(slime, buf, false);
        vespalib::Slime tmpSlime;
        size_t used = JsonFormat::decode(buf.get(), tmpSlime);
        EXPECT_TRUE(used > 0);
        slime = std::move(tmpSlime);
    }
    return slime;
}

bool
assertSlime(const std::string &exp, const DocsumReply &reply, uint32_t id, bool relaxed)
{
    vespalib::Slime slime = getSlime(reply, id, relaxed);
    vespalib::Slime expSlime;
    size_t used = JsonFormat::decode(exp, expSlime);
    EXPECT_TRUE(used > 0);
    return EXPECT_EQUAL(expSlime, slime);
}

TEST_F("requireThatAdapterHandlesAllFieldTypes", Fixture)
{
    Schema s;
    s.addSummaryField(Schema::SummaryField("a", schema::DataType::INT8));
    s.addSummaryField(Schema::SummaryField("b", schema::DataType::INT16));
    s.addSummaryField(Schema::SummaryField("c", schema::DataType::INT32));
    s.addSummaryField(Schema::SummaryField("d", schema::DataType::INT64));
    s.addSummaryField(Schema::SummaryField("e", schema::DataType::FLOAT));
    s.addSummaryField(Schema::SummaryField("f", schema::DataType::DOUBLE));
    s.addSummaryField(Schema::SummaryField("g", schema::DataType::STRING));
    s.addSummaryField(Schema::SummaryField("h", schema::DataType::STRING));
    s.addSummaryField(Schema::SummaryField("i", schema::DataType::RAW));
    s.addSummaryField(Schema::SummaryField("j", schema::DataType::RAW));
    s.addSummaryField(Schema::SummaryField("k", schema::DataType::STRING));
    s.addSummaryField(Schema::SummaryField("l", schema::DataType::STRING));

    BuildContext bc(s);
    bc._bld.startDocument("id:ns:searchdocument::0");
    bc._bld.startSummaryField("a").addInt(255).endField();
    bc._bld.startSummaryField("b").addInt(32767).endField();
    bc._bld.startSummaryField("c").addInt(2147483647).endField();
    bc._bld.startSummaryField("d").addInt(2147483648).endField();
    bc._bld.startSummaryField("e").addFloat(1234.56).endField();
    bc._bld.startSummaryField("f").addFloat(9876.54).endField();
    bc._bld.startSummaryField("g").addStr("foo").endField();
    bc._bld.startSummaryField("h").addStr("bar").endField();
    bc._bld.startSummaryField("i").addStr("baz").endField();
    bc._bld.startSummaryField("j").addStr("qux").endField();
    bc._bld.startSummaryField("k").addStr("<foo>").endField();
    bc._bld.startSummaryField("l").addStr("{foo:10}").endField();
    bc.endDocument(0);

    DocumentStoreAdapter dsa(bc._str,
                             *bc._repo,
                             f.getResultConfig(), "class0",
                             bc.createFieldCacheRepo(f.getResultConfig())->getFieldCache("class0"),
                             f.getMarkupFields());
    GeneralResultPtr res = getResult(dsa, 0);
    EXPECT_EQUAL(255u,        res->GetEntry("a")->_intval);
    EXPECT_EQUAL(32767u,      res->GetEntry("b")->_intval);
    EXPECT_EQUAL(2147483647u, res->GetEntry("c")->_intval);
    EXPECT_EQUAL(2147483648u, res->GetEntry("d")->_int64val);
    EXPECT_APPROX(1234.56,    res->GetEntry("e")->_doubleval, 10e-5);
    EXPECT_APPROX(9876.54,    res->GetEntry("f")->_doubleval, 10e-5);
    EXPECT_EQUAL("foo",       std::string(res->GetEntry("g")->_stringval,
                                        res->GetEntry("g")->_stringlen));
    EXPECT_EQUAL("bar",       std::string(res->GetEntry("h")->_stringval,
                                        res->GetEntry("h")->_stringlen));
    EXPECT_EQUAL("baz",       std::string(res->GetEntry("i")->_dataval,
                                        res->GetEntry("i")->_datalen));
    EXPECT_EQUAL("qux",       std::string(res->GetEntry("j")->_dataval,
                                        res->GetEntry("j")->_datalen));
    EXPECT_EQUAL("<foo>",     std::string(res->GetEntry("k")->_stringval,
                                        res->GetEntry("k")->_stringlen));
    EXPECT_EQUAL("{foo:10}",  std::string(res->GetEntry("l")->_stringval,
                                        res->GetEntry("l")->_stringlen));
}


TEST_F("requireThatAdapterHandlesMultipleDocuments", Fixture)
{
    Schema s;
    s.addSummaryField(Schema::SummaryField("a", schema::DataType::INT32));

    BuildContext bc(s);
    bc._bld.startDocument("id:ns:searchdocument::0").
        startSummaryField("a").
        addInt(1000).
        endField();
    bc.endDocument(0);
    bc._bld.startDocument("id:ns:searchdocument::1").
        startSummaryField("a").
        addInt(2000).endField();
    bc.endDocument(1);

    DocumentStoreAdapter dsa(bc._str, *bc._repo, f.getResultConfig(), "class1",
                             bc.createFieldCacheRepo(f.getResultConfig())->getFieldCache("class1"),
                             f.getMarkupFields());
    { // doc 0
        GeneralResultPtr res = getResult(dsa, 0);
        EXPECT_EQUAL(1000u, res->GetEntry("a")->_intval);
    }
    { // doc 1
        GeneralResultPtr res = getResult(dsa, 1);
        EXPECT_EQUAL(2000u, res->GetEntry("a")->_intval);
    }
    { // doc 2
        DocsumStoreValue docsum = dsa.getMappedDocsum(2);
        EXPECT_TRUE(docsum.pt() == nullptr);
    }
    { // doc 0 (again)
        GeneralResultPtr res = getResult(dsa, 0);
        EXPECT_EQUAL(1000u, res->GetEntry("a")->_intval);
    }
    EXPECT_EQUAL(0u, bc._str.lastSyncToken());
    uint64_t flushToken = bc._str.initFlush(bc._serialNum - 1);
    bc._str.flush(flushToken);
}


TEST_F("requireThatAdapterHandlesDocumentIdField", Fixture)
{
    Schema s;
    s.addSummaryField(Schema::SummaryField("documentid", schema::DataType::STRING));
    BuildContext bc(s);
    bc._bld.startDocument("id:ns:searchdocument::0").
        startSummaryField("documentid").
        addStr("foo").
        endField();
    bc.endDocument(0);
    DocumentStoreAdapter dsa(bc._str, *bc._repo, f.getResultConfig(), "class4",
                             bc.createFieldCacheRepo(f.getResultConfig())->getFieldCache("class4"),
                             f.getMarkupFields());
    GeneralResultPtr res = getResult(dsa, 0);
    EXPECT_EQUAL("id:ns:searchdocument::0", std::string(res->GetEntry("documentid")->_stringval,
                                     res->GetEntry("documentid")->_stringlen));
}


GlobalId gid1 = DocumentId("id:ns:searchdocument::1").getGlobalId(); // lid 1
GlobalId gid2 = DocumentId("id:ns:searchdocument::2").getGlobalId(); // lid 2
GlobalId gid3 = DocumentId("id:ns:searchdocument::3").getGlobalId(); // lid 3
GlobalId gid4 = DocumentId("id:ns:searchdocument::4").getGlobalId(); // lid 4
GlobalId gid9 = DocumentId("id:ns:searchdocument::9").getGlobalId(); // not existing

TEST("requireThatDocsumRequestIsProcessed")
{
    Schema s;
    s.addSummaryField(Schema::SummaryField("a", schema::DataType::INT32));

    BuildContext bc(s);
    DBContext dc(bc._repo, getDocTypeName());
    dc.put(*bc._bld.startDocument("id:ns:searchdocument::1").
           startSummaryField("a").
           addInt(10).
           endField().
           endDocument(),
           1);
    dc.put(*bc._bld.startDocument("id:ns:searchdocument::2").
           startSummaryField("a").
           addInt(20).
           endField().
           endDocument(),
           2);
    dc.put(*bc._bld.startDocument("id:ns:searchdocument::3").
           startSummaryField("a").
           addInt(30).
           endField().
           endDocument(),
           3);
    dc.put(*bc._bld.startDocument("id:ns:searchdocument::4").
           startSummaryField("a").
           addInt(40).
           endField().
           endDocument(),
           4);
    dc.put(*bc._bld.startDocument("id:ns:searchdocument::5").
           startSummaryField("a").
           addInt(50).
           endField().
           endDocument(),
           5);

    DocsumRequest req;
    req.resultClassName = "class1";
    req.hits.push_back(DocsumRequest::Hit(gid2));
    req.hits.push_back(DocsumRequest::Hit(gid4));
    req.hits.push_back(DocsumRequest::Hit(gid9));
    DocsumReply::UP rep = dc._ddb->getDocsums(req);

    EXPECT_EQUAL(3u, rep->docsums.size());
    EXPECT_EQUAL(2u, rep->docsums[0].docid);
    EXPECT_EQUAL(gid2, rep->docsums[0].gid);
    EXPECT_TRUE(assertSlime("{a:20}", *rep, 0, false));
    EXPECT_EQUAL(4u, rep->docsums[1].docid);
    EXPECT_EQUAL(gid4, rep->docsums[1].gid);
    EXPECT_TRUE(assertSlime("{a:40}", *rep, 1, false));
    EXPECT_EQUAL(search::endDocId, rep->docsums[2].docid);
    EXPECT_EQUAL(gid9, rep->docsums[2].gid);
    EXPECT_TRUE(rep->docsums[2].data.get() == nullptr);
}


TEST("requireThatRewritersAreUsed")
{
    Schema s;
    s.addSummaryField(Schema::SummaryField("aa", schema::DataType::INT32));
    s.addSummaryField(Schema::SummaryField("ab", schema::DataType::INT32));

    BuildContext bc(s);
    DBContext dc(bc._repo, getDocTypeName());
    dc.put(*bc._bld.startDocument("id:ns:searchdocument::1").
           startSummaryField("aa").
           addInt(10).
           endField().
           startSummaryField("ab").
           addInt(20).
           endField().
           endDocument(),
           1);

    DocsumRequest req;
    req.resultClassName = "class2";
    req.hits.push_back(DocsumRequest::Hit(gid1));
    DocsumReply::UP rep = dc._ddb->getDocsums(req);
    EXPECT_EQUAL(1u, rep->docsums.size());
    EXPECT_TRUE(assertSlime("{aa:20}", *rep, 0, false));
}

TEST("requireThatSummariesTimeout")
{
    Schema s;
    s.addSummaryField(Schema::SummaryField("aa", schema::DataType::INT32));
    s.addSummaryField(Schema::SummaryField("ab", schema::DataType::INT32));

    BuildContext bc(s);
    DBContext dc(bc._repo, getDocTypeName());
    dc.put(*bc._bld.startDocument("id:ns:searchdocument::1").
                   startSummaryField("aa").
                   addInt(10).
                   endField().
                   startSummaryField("ab").
                   addInt(20).
                   endField().
                   endDocument(),
           1);

    DocsumRequest req;
    req.setTimeout(vespalib::duration::zero());
    EXPECT_TRUE(req.expired());
    req.resultClassName = "class2";
    req.hits.push_back(DocsumRequest::Hit(gid1));
    DocsumReply::UP rep = dc._ddb->getDocsums(req);
    EXPECT_EQUAL(1u, rep->docsums.size());
    vespalib::SimpleBuffer buf;
    vespalib::Slime summary = getSlime(*rep, 0, false);
    JsonFormat::encode(summary, buf, false);
    auto bufstring = buf.get().make_stringref(); 
    EXPECT_TRUE(std::regex_search(bufstring.data(), bufstring.data() + bufstring.size(), std::regex("Timed out with -[0-9]+us left.")));
}

void
addField(Schema & s,
         const std::string &name,
         Schema::DataType dtype,
         Schema::CollectionType ctype,
         const std::string& tensor_spec = "")
{
    s.addSummaryField(Schema::SummaryField(name, dtype, ctype, tensor_spec));
    s.addAttributeField(Schema::AttributeField(name, dtype, ctype, tensor_spec));
}


TEST("requireThatAttributesAreUsed")
{
    Schema s;
    addField(s, "ba", schema::DataType::INT32, CollectionType::SINGLE);
    addField(s, "bb", schema::DataType::FLOAT, CollectionType::SINGLE);
    addField(s, "bc", schema::DataType::STRING, CollectionType::SINGLE);
    addField(s, "bd", schema::DataType::INT32, CollectionType::ARRAY);
    addField(s, "be", schema::DataType::FLOAT, CollectionType::ARRAY);
    addField(s, "bf", schema::DataType::STRING, CollectionType::ARRAY);
    addField(s, "bg", schema::DataType::INT32, CollectionType::WEIGHTEDSET);
    addField(s, "bh", schema::DataType::FLOAT, CollectionType::WEIGHTEDSET);
    addField(s, "bi", schema::DataType::STRING, CollectionType::WEIGHTEDSET);
    addField(s, "bj", schema::DataType::TENSOR, CollectionType::SINGLE, "tensor(x{},y{})");

    BuildContext bc(s);
    DBContext dc(bc._repo, getDocTypeName());
    dc.put(*bc._bld.startDocument("id:ns:searchdocument::1").
           endDocument(),
           1); // empty doc
    dc.put(*bc._bld.startDocument("id:ns:searchdocument::2").
           startAttributeField("ba").
           addInt(10).
           endField().
           startAttributeField("bb").
           addFloat(10.1).
           endField().
           startAttributeField("bc").
           addStr("foo").
           endField().
           startAttributeField("bd").
           startElement().
           addInt(20).
           endElement().
           startElement().
           addInt(30).
           endElement().
           endField().
           startAttributeField("be").
           startElement().
           addFloat(20.2).
           endElement().
           startElement().
           addFloat(30.3).
           endElement().
           endField().
           startAttributeField("bf").
           startElement().
           addStr("bar").
           endElement().
           startElement().
           addStr("baz").
           endElement().
           endField().
           startAttributeField("bg").
           startElement(2).
           addInt(40).
           endElement().
           startElement(3).
           addInt(50).
           endElement().
           endField().
           startAttributeField("bh").
           startElement(4).
           addFloat(40.4).
           endElement().
           startElement(5).
           addFloat(50.5).
           endElement().
           endField().
           startAttributeField("bi").
           startElement(7).
           addStr("quux").
           endElement().
           startElement(6).
           addStr("qux").
           endElement().
           endField().
           startAttributeField("bj").
           addTensor(make_tensor(TensorSpec("tensor(x{},y{})")
                                 .add({{"x", "f"}, {"y", "g"}}, 3))).
           endField().
           endDocument(),
           2);
    dc.put(*bc._bld.startDocument("id:ns:searchdocument::3").
           endDocument(),
           3); // empty doc

    DocsumRequest req;
    req.resultClassName = "class3";
    req.hits.push_back(DocsumRequest::Hit(gid2));
    req.hits.push_back(DocsumRequest::Hit(gid3));
    DocsumReply::UP rep = dc._ddb->getDocsums(req);
    uint32_t rclass = 3;

    EXPECT_EQUAL(2u, rep->docsums.size());

    // FIXME the expected output ordering of weighted set fields is currently inherently linked
    // to the internal ordering of such attributes. Should be decoupled, as this is very fragile.
    EXPECT_TRUE(assertSlime("{ba:10,bb:10.1,"
                            "bc:'foo',"
                            "bd:[20,30],"
                            "be:[20.2,30.3],"
                            "bf:['bar','baz'],"
                            "bg:[{item:40,weight:2},{item:50,weight:3}],"
                            "bh:[{item:50.5,weight:5},{item:40.4,weight:4}],"
                            "bi:[{item:'quux',weight:7},{item:'qux',weight:6}],"
                            "bj:'0x01020178017901016601674008000000000000'}", *rep, 0, true));
    TEST_DO(assertTensor(make_tensor(TensorSpec("tensor(x{},y{})")
                                     .add({{"x", "f"}, {"y", "g"}}, 3)),
                         "bj", *rep, 0, rclass));

    // empty doc
    EXPECT_TRUE(assertSlime("{}", *rep, 1, false));
    TEST_DO(assertTensor(vespalib::eval::Value::UP(), "bj", *rep, 1, rclass));

    proton::IAttributeManager::SP attributeManager = dc._ddb->getReadySubDB()->getAttributeManager();
    vespalib::ISequencedTaskExecutor &attributeFieldWriter = attributeManager->getAttributeFieldWriter();
    search::AttributeVector *bjAttr = attributeManager->getWritableAttribute("bj");
    auto bjTensorAttr = dynamic_cast<search::tensor::TensorAttribute *>(bjAttr);

    attributeFieldWriter.execute(attributeFieldWriter.getExecutorIdFromName(bjAttr->getNamePrefix()),
                                 [&]() {
                                     bjTensorAttr->setTensor(3, *make_tensor(TensorSpec("tensor(x{},y{})")
                                                     .add({{"x", "a"}, {"y", "b"}}, 4)));
                                     bjTensorAttr->commit();
                                 });
    attributeFieldWriter.sync();

    DocsumReply::UP rep2 = dc._ddb->getDocsums(req);
    TEST_DO(assertTensor(make_tensor(TensorSpec("tensor(x{},y{})")
                                     .add({{"x", "a"}, {"y", "b"}}, 4)),
                         "bj", *rep2, 1, rclass));

    DocsumRequest req3;
    req3.resultClassName = "class3";
    req3.hits.push_back(DocsumRequest::Hit(gid3));
    DocsumReply::UP rep3 = dc._ddb->getDocsums(req3);

    EXPECT_TRUE(assertSlime("{bj:'0x01020178017901016101624010000000000000'}",
                            *rep3, 0, true));
}


TEST("requireThatSummaryAdapterHandlesPutAndRemove")
{
    Schema s;
    s.addSummaryField(Schema::SummaryField("f1", schema::DataType::STRING, CollectionType::SINGLE));
    BuildContext bc(s);
    DBContext dc(bc._repo, getDocTypeName());
    Document::UP exp = bc._bld.startDocument("id:ns:searchdocument::1").
                       startSummaryField("f1").
                       addStr("foo").
                       endField().
                       endDocument();
    dc._sa->put(1, 1, *exp);
    IDocumentStore & store = dc._ddb->getReadySubDB()->getSummaryManager()->getBackingStore();
    Document::UP act = store.read(1, *bc._repo);
    EXPECT_TRUE(act.get() != nullptr);
    EXPECT_EQUAL(exp->getType(), act->getType());
    EXPECT_EQUAL("foo", act->getValue("f1")->toString());
    dc._sa->remove(2, 1);
    EXPECT_TRUE(store.read(1, *bc._repo).get() == nullptr);
}


const std::string TERM_ORIG = "\357\277\271";
const std::string TERM_INDEX = "\357\277\272";
const std::string TERM_END = "\357\277\273";
const std::string TERM_SEP = "\037";
const std::string TERM_EMPTY = "";
namespace
{
  const std::string empty;
}

TEST_F("requireThatAnnotationsAreUsed", Fixture)
{
    Schema s;
    s.addIndexField(Schema::IndexField("g", schema::DataType::STRING, CollectionType::SINGLE));
    s.addSummaryField(Schema::SummaryField("g", schema::DataType::STRING, CollectionType::SINGLE));
    s.addIndexField(Schema::IndexField("dynamicstring", schema::DataType::STRING, CollectionType::SINGLE));
    s.addSummaryField(Schema::SummaryField("dynamicstring", schema::DataType::STRING, CollectionType::SINGLE));
    BuildContext bc(s);
    DBContext dc(bc._repo, getDocTypeName());
    Document::UP exp = bc._bld.startDocument("id:ns:searchdocument::0").
                       startIndexField("g").
                       addStr("foo").
                       addStr("bar").
                       addTermAnnotation("baz").
                       endField().
                       startIndexField("dynamicstring").
                       setAutoAnnotate(false).
                       addStr("foo").
                       addSpan().
                       addAlphabeticTokenAnnotation().
                       addTermAnnotation().
                       addNoWordStr(" ").
                       addSpan().
                       addSpaceTokenAnnotation().
                       addStr("bar").
                       addSpan().
                       addAlphabeticTokenAnnotation().
                       addTermAnnotation("baz").
                       setAutoAnnotate(true).
                       endField().
                       endDocument();
    dc._sa->put(1, 1, *exp);

    IDocumentStore & store = dc._ddb->getReadySubDB()->getSummaryManager()->getBackingStore();
    Document::UP act = store.read(1, *bc._repo);
    EXPECT_TRUE(act.get() != nullptr);
    EXPECT_EQUAL(exp->getType(), act->getType());
    EXPECT_EQUAL("foo bar", act->getValue("g")->getAsString());
    EXPECT_EQUAL("foo bar", act->getValue("dynamicstring")->getAsString());

    DocumentStoreAdapter dsa(store, *bc._repo, f.getResultConfig(), "class0",
                             bc.createFieldCacheRepo(f.getResultConfig())->getFieldCache("class0"),
                             f.getMarkupFields());
    EXPECT_TRUE(assertString("foo bar", "g", dsa, 1));
    EXPECT_TRUE(assertString(TERM_EMPTY + "foo" + TERM_SEP +
                            " " + TERM_SEP +
                            TERM_ORIG + "bar" + TERM_INDEX + "baz" + TERM_END +
                            TERM_SEP,
                            "dynamicstring", dsa, 1));
}

TEST_F("requireThatUrisAreUsed", Fixture)
{
    Schema s;
    s.addUriIndexFields(Schema::IndexField("urisingle", schema::DataType::STRING, CollectionType::SINGLE));
    s.addSummaryField(Schema::SummaryField("urisingle", schema::DataType::STRING, CollectionType::SINGLE));
    s.addUriIndexFields(Schema::IndexField("uriarray", schema::DataType::STRING, CollectionType::ARRAY));
    s.addSummaryField(Schema::SummaryField("uriarray", schema::DataType::STRING, CollectionType::ARRAY));
    s.addUriIndexFields(Schema::IndexField("uriwset", schema::DataType::STRING, CollectionType::WEIGHTEDSET));
    s.addSummaryField(Schema::SummaryField("uriwset", schema::DataType::STRING, CollectionType::WEIGHTEDSET));
    BuildContext bc(s);
    DBContext dc(bc._repo, getDocTypeName());
    Document::UP exp = bc._bld.startDocument("id:ns:searchdocument::0").
                       startIndexField("urisingle").
                       startSubField("all").
                       addUrlTokenizedString("http://www.example.com:81/fluke?ab=2#4").
                       endSubField().
                       startSubField("scheme").
                       addUrlTokenizedString("http").
                       endSubField().
                       startSubField("host").
                       addUrlTokenizedString("www.example.com").
                       endSubField().
                       startSubField("port").
                       addUrlTokenizedString("81").
                       endSubField().
                       startSubField("path").
                       addUrlTokenizedString("/fluke").
                       endSubField().
                       startSubField("query").
                       addUrlTokenizedString("ab=2").
                       endSubField().
                       startSubField("fragment").
                       addUrlTokenizedString("4").
                       endSubField().
                       endField().
                       startIndexField("uriarray").
                       startElement(1).
                       startSubField("all").
                       addUrlTokenizedString("http://www.example.com:82/fluke?ab=2#8").
                       endSubField().
                       startSubField("scheme").
                       addUrlTokenizedString("http").
                       endSubField().
                       startSubField("host").
                       addUrlTokenizedString("www.example.com").
                       endSubField().
                       startSubField("port").
                       addUrlTokenizedString("82").
                       endSubField().
                       startSubField("path").
                       addUrlTokenizedString("/fluke").
                       endSubField().
                       startSubField("query").
                       addUrlTokenizedString("ab=2").
                       endSubField().
                       startSubField("fragment").
                       addUrlTokenizedString("8").
                       endSubField().
                       endElement().
                       startElement(1).
                       startSubField("all").
                       addUrlTokenizedString("http://www.flickr.com:82/fluke?ab=2#9").
                       endSubField().
                       startSubField("scheme").
                       addUrlTokenizedString("http").
                       endSubField().
                       startSubField("host").
                       addUrlTokenizedString("www.flickr.com").
                       endSubField().
                       startSubField("port").
                       addUrlTokenizedString("82").
                       endSubField().
                       startSubField("path").
                       addUrlTokenizedString("/fluke").
                       endSubField().
                       startSubField("query").
                       addUrlTokenizedString("ab=2").
                       endSubField().
                       startSubField("fragment").
                       addUrlTokenizedString("9").
                       endSubField().
                       endElement().
                       endField().
                       startIndexField("uriwset").
                       startElement(4).
                       startSubField("all").
                       addUrlTokenizedString("http://www.example.com:83/fluke?ab=2#12").
                       endSubField().
                       startSubField("scheme").
                       addUrlTokenizedString("http").
                       endSubField().
                       startSubField("host").
                       addUrlTokenizedString("www.example.com").
                       endSubField().
                       startSubField("port").
                       addUrlTokenizedString("83").
                       endSubField().
                       startSubField("path").
                       addUrlTokenizedString("/fluke").
                       endSubField().
                       startSubField("query").
                       addUrlTokenizedString("ab=2").
                       endSubField().
                       startSubField("fragment").
                       addUrlTokenizedString("12").
                       endSubField().
                       endElement().
                       startElement(7).
                       startSubField("all").
                       addUrlTokenizedString("http://www.flickr.com:85/fluke?ab=2#13").
                       endSubField().
                       startSubField("scheme").
                       addUrlTokenizedString("http").
                       endSubField().
                       startSubField("host").
                       addUrlTokenizedString("www.flickr.com").
                       endSubField().
                       startSubField("port").
                       addUrlTokenizedString("85").
                       endSubField().
                       startSubField("path").
                       addUrlTokenizedString("/fluke").
                       endSubField().
                       startSubField("query").
                       addUrlTokenizedString("ab=2").
                       endSubField().
                       startSubField("fragment").
                       addUrlTokenizedString("13").
                       endSubField().
                       endElement().
                       endField().
                       endDocument();
    dc._sa->put(1, 1, *exp);

    IDocumentStore & store =
        dc._ddb->getReadySubDB()->getSummaryManager()->getBackingStore();
    Document::UP act = store.read(1, *bc._repo);
    EXPECT_TRUE(act.get() != nullptr);
    EXPECT_EQUAL(exp->getType(), act->getType());

    DocumentStoreAdapter dsa(store, *bc._repo, f.getResultConfig(), "class0",
                             bc.createFieldCacheRepo(f.getResultConfig())->getFieldCache("class0"),
                             f.getMarkupFields());

    EXPECT_TRUE(assertString("http://www.example.com:81/fluke?ab=2#4", "urisingle", dsa, 1));
    GeneralResultPtr res = getResult(dsa, 1);
    {
        vespalib::Slime slime;
        decode(res->GetEntry("uriarray"), slime);
        EXPECT_TRUE(slime.get().valid());
        EXPECT_EQUAL("http://www.example.com:82/fluke?ab=2#8",  asVstring(slime.get()[0]));
        EXPECT_EQUAL("http://www.flickr.com:82/fluke?ab=2#9", asVstring(slime.get()[1]));
    }
    {
        vespalib::Slime slime;
        decode(res->GetEntry("uriwset"), slime);
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
    Schema s;
    s.addAttributeField(Schema::AttributeField("sp2", schema::DataType::INT64));
    s.addAttributeField(Schema::AttributeField("ap2", schema::DataType::INT64, CollectionType::ARRAY));
    s.addAttributeField(Schema::AttributeField("wp2", schema::DataType::INT64, CollectionType::WEIGHTEDSET));

    BuildContext bc(s);
    DBContext dc(bc._repo, getDocTypeName());
    Document::UP exp = bc._bld.startDocument("id:ns:searchdocument::1").
                       startAttributeField("sp2").
                       addPosition(1002, 1003).
                       endField().
                       startAttributeField("ap2").
                       startElement().addPosition(1006, 1007).endElement().
                       startElement().addPosition(1008, 1009).endElement().
                       endField().
                       startAttributeField("wp2").
                       startElement(43).addPosition(1012, 1013).endElement().
                       startElement(44).addPosition(1014, 1015).endElement().
                       endField().
                       endDocument();
    dc.put(*exp, 1);

    IDocumentStore & store = dc._ddb->getReadySubDB()->getSummaryManager()->getBackingStore();
    Document::UP act = store.read(1, *bc._repo);
    EXPECT_TRUE(act);
    EXPECT_EQUAL(exp->getType(), act->getType());

    DocsumRequest req;
    req.resultClassName = "class5";
    req.hits.push_back(DocsumRequest::Hit(gid1));
    DocsumReply::UP rep = dc._ddb->getDocsums(req);
    // uint32_t rclass = 5;

    EXPECT_EQUAL(1u, rep->docsums.size());
    EXPECT_EQUAL(1u, rep->docsums[0].docid);
    EXPECT_EQUAL(gid1, rep->docsums[0].gid);
    EXPECT_TRUE(assertSlime("{sp2:'1047758'"
                            ",sp2x:{x:1002, y:1003, latlong:'N0.001003;E0.001002'}"
                            ",ap2:[1047806,1048322]"
                            ",ap2x:[{x:1006, y:1007, latlong:'N0.001007;E0.001006'},"
                                   "{x:1008, y:1009, latlong:'N0.001009;E0.001008'}]"
                            ",wp2:[{item:1048370,weight:43},{item:1048382,weight:44}]"
                            ",wp2x:[{ x:1012, y:1013, latlong:'N0.001013;E0.001012'},"
                                   "{ x:1014, y:1015, latlong:'N0.001015;E0.001014'}]}",
                            *rep, 0, false));
}


TEST_F("requireThatRawFieldsWorks", Fixture)
{
    Schema s;
    s.addSummaryField(Schema::AttributeField("i", schema::DataType::RAW));
    s.addSummaryField(Schema::AttributeField("araw", schema::DataType::RAW, CollectionType::ARRAY));
    s.addSummaryField(Schema::AttributeField("wraw", schema::DataType::RAW, CollectionType::WEIGHTEDSET));

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

    BuildContext bc(s);
    DBContext dc(bc._repo, getDocTypeName());
    Document::UP exp = bc._bld.startDocument("id:ns:searchdocument::0").
                       startSummaryField("i").
                       addRaw(raw1s.c_str(), raw1s.size()).
                       endField().
                       startSummaryField("araw").
                       startElement().
                       addRaw(raw1a0.c_str(), raw1a0.size()).
                       endElement().
                       startElement().
                       addRaw(raw1a1.c_str(), raw1a1.size()).
                       endElement().
                       endField().
                       startSummaryField("wraw").
                       startElement(46).
                       addRaw(raw1w1.c_str(), raw1w1.size()).
                       endElement().
                       startElement(45).
                       addRaw(raw1w0.c_str(), raw1w0.size()).
                       endElement().
                       endField().
                       endDocument();
    dc._sa->put(1, 1, *exp);

    IDocumentStore & store = dc._ddb->getReadySubDB()->getSummaryManager()->getBackingStore();
    Document::UP act = store.read(1, *bc._repo);
    EXPECT_TRUE(act.get() != nullptr);
    EXPECT_EQUAL(exp->getType(), act->getType());

    DocumentStoreAdapter dsa(store, *bc._repo, f.getResultConfig(), "class0",
                             bc.createFieldCacheRepo(f.getResultConfig())->getFieldCache("class0"),
                             f.getMarkupFields());

    ASSERT_TRUE(assertString(raw1s, "i", dsa, 1));

    GeneralResultPtr res = getResult(dsa, 1);
    {
        vespalib::Slime slime;
        decode(res->GetEntry("araw"), slime);
        EXPECT_TRUE(slime.get().valid());
        EXPECT_EQUAL(vespalib::Base64::encode(raw1a0), b64encode(slime.get()[0]));
        EXPECT_EQUAL(vespalib::Base64::encode(raw1a1), b64encode(slime.get()[1]));
    }
    {
        vespalib::Slime slime;
        decode(res->GetEntry("wraw"), slime);
        EXPECT_TRUE(slime.get().valid());
        EXPECT_EQUAL(46L, slime.get()[0]["weight"].asLong());
        EXPECT_EQUAL(45L, slime.get()[1]["weight"].asLong());
        std::string arr0s = b64encode(slime.get()[0]["item"]);
        std::string arr1s = b64encode(slime.get()[1]["item"]);
        EXPECT_EQUAL(vespalib::Base64::encode(raw1w1), arr0s);
        EXPECT_EQUAL(vespalib::Base64::encode(raw1w0), arr1s);
    }
}


TEST_F("requireThatFieldCacheRepoCanReturnDefaultFieldCache", Fixture)
{
    Schema s;
    s.addSummaryField(Schema::SummaryField("a", schema::DataType::INT32));
    BuildContext bc(s);
    FieldCacheRepo::UP repo = bc.createFieldCacheRepo(f.getResultConfig());
    FieldCache::CSP cache = repo->getFieldCache("");
    EXPECT_TRUE(cache.get() == repo->getFieldCache("class1").get());
    EXPECT_EQUAL(1u, cache->size());
    EXPECT_EQUAL("a", cache->getField(0)->getName());
}


Fixture::Fixture()
    : _summaryCfg(),
      _resultCfg(),
      _markupFields()
{
    std::string cfgId("summary");
    _summaryCfg = config::ConfigGetter<vespa::config::search::SummaryConfig>::getConfig(
        cfgId, config::FileSpec(TEST_PATH("summary.cfg")));
    _resultCfg.ReadConfig(*_summaryCfg, cfgId.c_str());
    std::string mapCfgId("summarymap");
    std::unique_ptr<vespa::config::search::SummarymapConfig> mapCfg = config::ConfigGetter<vespa::config::search::SummarymapConfig>::getConfig(
            mapCfgId, config::FileSpec(TEST_PATH("summarymap.cfg")));
    for (size_t i = 0; i < mapCfg->override.size(); ++i) {
        const vespa::config::search::SummarymapConfig::Override & o = mapCfg->override[i];
        if (o.command == "dynamicteaser") {
            vespalib::string markupField = o.arguments;
            if (markupField.empty())
                continue;
            // Assume just one argument: source field that must contain markup
            _markupFields.insert(markupField);
            LOG(info, "Field %s has markup", markupField.c_str());
        }
    }
}

Fixture::~Fixture() = default;

}

TEST_MAIN() { TEST_RUN_ALL(); }
