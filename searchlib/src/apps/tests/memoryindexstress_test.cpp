// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include <vespa/searchlib/common/scheduletaskcallback.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/memoryindex/memory_index.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>
#include <vespa/searchlib/queryeval/fake_searchable.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/test/index/mock_field_length_inspector.h>
#include <vespa/document/annotation/spanlist.h>
#include <vespa/document/annotation/spantree.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/repo/fixedtyperepo.h>
#include <vespa/vespalib/util/rand48.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/testkit/test_master.hpp>

#include <vespa/log/log.h>
LOG_SETUP("memoryindexstress_test");

using document::AnnotationType;
using document::Document;
using document::DocumentId;
using document::DocumentType;
using document::DocumentTypeRepo;
using document::FieldValue;
using document::Span;
using document::SpanList;
using document::StringFieldValue;
using namespace search::fef;
using namespace search::index;
using namespace search::memoryindex;
using namespace search::queryeval;
using search::ScheduleTaskCallback;
using search::index::schema::DataType;
using search::query::Node;
using search::query::SimplePhrase;
using search::query::SimpleStringTerm;
using search::index::test::MockFieldLengthInspector;
using vespalib::IDestructorCallback;
using vespalib::asciistream;
using vespalib::makeLambdaTask;

namespace {

const vespalib::string SPANTREE_NAME("linguistics");
const vespalib::string title("title");
const vespalib::string body("body");
const vespalib::string foo("foo");
const vespalib::string bar("bar");
const vespalib::string doc_type_name = "test";
const vespalib::string header_name = doc_type_name + ".header";
const vespalib::string body_name = doc_type_name + ".body";
uint32_t docid_limit = 100; // needed for relative estimates

Schema
makeSchema()
{
    Schema schema;
    schema.addIndexField(Schema::IndexField(title, DataType::STRING));
    schema.addIndexField(Schema::IndexField(body, DataType::STRING));
    return schema;
}

document::config::DocumenttypesConfig
makeDocTypeRepoConfig()
{
    const int32_t doc_type_id = 787121340;
    document::config_builder::DocumenttypesConfigBuilderHelper builder;
    builder.document(doc_type_id,
                     doc_type_name,
                     document::config_builder::Struct(header_name),
                     document::config_builder::Struct(body_name).
                     addField(title, document::DataType::T_STRING).
                     addField(body, document::DataType::T_STRING));
    return builder.config();
}


bool isWordChar(char c) {
    return ((c >= '0' && c <= '9') ||
            (c >= 'A' && c <= 'Z') ||
            (c >= 'a' && c <= 'z'));
}


void
tokenizeStringFieldValue(const document::FixedTypeRepo & repo, StringFieldValue &field)
{
    document::SpanTree::UP spanTree;
    SpanList::UP spanList(std::make_unique<SpanList>());
    SpanList *spans = spanList.get();
    spanTree.reset(new document::SpanTree(SPANTREE_NAME, std::move(spanList)));
    const vespalib::string &text = field.getValue();
    uint32_t cur = 0;
    int32_t start = 0;
    bool inWord = false;
    for (cur = 0; cur < text.size(); ++cur) {
        char c = text[cur];
        bool isWc = isWordChar(c);
        if (!inWord && isWc) {
            inWord = true;
            start = cur;
        } else if (inWord && !isWc) {
            int32_t len = cur - start;
            spanTree->annotate(spans->add(std::make_unique<Span>(start, len)),
                               *AnnotationType::TERM);
            inWord = false;
        }
    }
    if (inWord) {
        int32_t len = cur - start;
        spanTree->annotate(spans->add(std::make_unique<Span>(start, len)),
                           *AnnotationType::TERM);
    }
    if (spanTree->numAnnotations() > 0u) {
        StringFieldValue::SpanTrees trees;
        trees.emplace_back(std::move(spanTree));
        field.setSpanTrees(trees, repo);
    }
}


void
setFieldValue(Document &doc, const vespalib::string &fieldName,
              const vespalib::string &fieldString)
{
    std::unique_ptr<StringFieldValue> fieldValue =
        StringFieldValue::make(fieldString);
    document::FixedTypeRepo repo(*doc.getRepo(), doc.getType());
    tokenizeStringFieldValue(repo, *fieldValue);
    doc.setFieldValue(doc.getField(fieldName), std::move(fieldValue));
}

Document::UP
makeDoc(const DocumentTypeRepo &repo, uint32_t i,
        const vespalib::string &titleString,
        const vespalib::string &bodyString = "")
{
    asciistream idstr;
    idstr << "id:test:test:: " << i;
    DocumentId id(idstr.str());
    const DocumentType *docType = repo.getDocumentType(doc_type_name);
    auto doc(std::make_unique<Document>(repo, *docType, id));
    if (!titleString.empty()) {
        setFieldValue(*doc, title, titleString);
    }
    if (!bodyString.empty()) {
        setFieldValue(*doc, body, bodyString);
    }
    ASSERT_TRUE(doc.get());
#if 0
    doc->print(std::cout, true, "");
    std::cout << std::endl;
#endif
    return doc;
}

Document::UP
makeDoc(const DocumentTypeRepo &repo, uint32_t i)
{
    asciistream titleStr;
    asciistream bodyStr;
    titleStr << i;
    bodyStr << (i * 3);
    return makeDoc(repo, i, titleStr.str(), bodyStr.str());
}


SimpleStringTerm makeTerm(std::string_view term) {
    return SimpleStringTerm(term, "field", 0, search::query::Weight(0));
}

Node::UP makePhrase(const std::string &term1, const std::string &term2) {
    SimplePhrase * phrase = new SimplePhrase("field", 0, search::query::Weight(0));
    Node::UP node(phrase);
    phrase->append(std::make_unique<SimpleStringTerm>(makeTerm(term1)));
    phrase->append(std::make_unique<SimpleStringTerm>(makeTerm(term2)));
    return node;
}

class HoldDoc : public IDestructorCallback {
    std::unique_ptr<Document> _doc;
public:
    HoldDoc(std::unique_ptr<Document> doc) noexcept
        : _doc(std::move(doc))
    {
    }
    ~HoldDoc() override = default;
};

}  // namespace

struct Fixture {
    Schema       schema;
    DocumentTypeRepo  repo;
    vespalib::ThreadStackExecutor _executor;
    std::unique_ptr<vespalib::ISequencedTaskExecutor> _invertThreads;
    std::unique_ptr<vespalib::ISequencedTaskExecutor> _pushThreads;
    MemoryIndex  index;
    uint32_t _readThreads;
    vespalib::ThreadStackExecutor _writer; // 1 write thread
    vespalib::ThreadStackExecutor _readers; // multiple reader threads
    vespalib::Rand48 _rnd;
    uint32_t _keyLimit;
    std::atomic<long> _readSeed;
    std::atomic<long> _doneWriteWork;
    std::atomic<long> _doneReadWork;
    std::atomic<long> _emptyCount;
    std::atomic<long> _nonEmptyCount;
    std::atomic<int> _stopRead;
    bool _reportWork;

    Fixture(uint32_t readThreads = 1);

    ~Fixture();

    void internalSyncCommit() {
        vespalib::Gate gate;
        index.commit(std::make_shared<ScheduleTaskCallback>
                     (_executor,
                      makeLambdaTask([&]() { gate.countDown(); })));
        gate.await();
    }
    void put(uint32_t id, Document::UP doc) {
        auto& docref = *doc;
        index.insertDocument(id, docref, std::make_shared<HoldDoc>(std::move(doc)));
    }
     void remove(uint32_t id) {
        std::vector<uint32_t> lids;
        lids.push_back(id);
        index.removeDocuments(std::move(lids));
    }

    void readWork(uint32_t cnt);
    void readWork();
    void writeWork(uint32_t cnt);
    uint32_t getReadThreads() const { return _readThreads; }
    void stressTest(uint32_t writeCnt);

private:
    Fixture(const Fixture &index) = delete;
    Fixture(Fixture &&index) = delete;
    Fixture &operator=(const Fixture &index) = delete;
    Fixture &operator=(Fixture &&index) = delete;
};

VESPA_THREAD_STACK_TAG(invert_executor)
VESPA_THREAD_STACK_TAG(push_executor)

Fixture::Fixture(uint32_t readThreads)
    : schema(makeSchema()),
      repo(makeDocTypeRepoConfig()),
      _executor(1),
      _invertThreads(vespalib::SequencedTaskExecutor::create(invert_executor, 2)),
      _pushThreads(vespalib::SequencedTaskExecutor::create(push_executor, 2)),
      index(schema, MockFieldLengthInspector(), *_invertThreads, *_pushThreads),
      _readThreads(readThreads),
      _writer(1),
      _readers(readThreads),
      _rnd(),
      _keyLimit(1000000),
      _readSeed(50),
      _doneWriteWork(0),
      _doneReadWork(0),
      _emptyCount(0),
      _nonEmptyCount(0),
      _stopRead(0),
      _reportWork(false)
{
    _rnd.srand48(32);
}


Fixture::~Fixture()
{
    _readers.sync();
    _readers.shutdown();
    _writer.sync();
    _writer.shutdown();
    if (_reportWork) {
        LOG(info,
            "readWork=%ld, writeWork=%ld, emptyCount=%ld, nonemptyCount=%ld",
            _doneReadWork.load(), _doneWriteWork.load(),
            _emptyCount.load(), _nonEmptyCount.load());
    }
}


void
Fixture::readWork(uint32_t cnt)
{
    vespalib::Rand48 rnd;
    rnd.srand48(++_readSeed);
    uint32_t i;
    uint32_t emptyCount = 0;
    uint32_t nonEmptyCount = 0;
    std::string fieldName = "title";

    for (i = 0; i < cnt && _stopRead.load() == 0; ++i) {
        uint32_t key = (rnd.lrand48() % (_keyLimit + 1)) + 1;

        asciistream keyStr;
        keyStr << key;

        SimpleStringTerm term = makeTerm(keyStr.str());

        uint32_t fieldId = 0;
        FakeRequestContext requestContext;

        MatchDataLayout mdl;
        TermFieldHandle handle = mdl.allocTermField(fieldId);
        MatchData::UP match_data = mdl.createMatchData();

        FieldSpec field(fieldName, fieldId, handle);
        FieldSpecList fields;
        fields.add(field);
        Blueprint::UP result = index.createBlueprint(requestContext, fields, term);
        if (!EXPECT_TRUE(result.get() != 0)) {
            LOG(error, "Did not get blueprint");
            break;
        }
        result->basic_plan(true, docid_limit);
        if (result->getState().estimate().empty) {
            ++emptyCount;
        } else {
            ++nonEmptyCount;
        }
        result->fetchPostings(ExecuteInfo::FULL);
        SearchIterator::UP search = result->createSearch(*match_data);
        if (!EXPECT_TRUE(search)) {
            LOG(error, "Did not get search iterator");
            break;
        }
    }
    _doneReadWork += i;
    _emptyCount += emptyCount;
    _nonEmptyCount += nonEmptyCount;
    LOG(info, "done %u read work", i);
}


void
Fixture::readWork()
{
    readWork(std::numeric_limits<uint32_t>::max());
}


void
Fixture::writeWork(uint32_t cnt)
{
    vespalib::Rand48 &rnd(_rnd);
    for (uint32_t i = 0; i < cnt; ++i) {
        uint32_t key = rnd.lrand48() % _keyLimit;
        if ((rnd.lrand48() & 1) == 0) {
            put(key + 1, makeDoc(repo, key + 1));
        } else {
            remove(key + 1);
        }
        internalSyncCommit();
    }
    _doneWriteWork += cnt;
    _stopRead = 1;
    LOG(info, "done %u write work", cnt);
}


void
Fixture::stressTest(uint32_t writeCnt)
{
    _reportWork = true;
    uint32_t readThreads = getReadThreads();
    LOG(info,
        "starting stress test, 1 write thread, %u read threads, %u writes",
        readThreads, writeCnt);
    _writer.execute(makeLambdaTask([this, writeCnt]() { writeWork(writeCnt); }));
    for (uint32_t i = 0; i < readThreads; ++i) {
        _readers.execute(makeLambdaTask([this]() { readWork(); }));
    }
}


//-----------------------------------------------------------------------------

std::string toString(SearchIterator & search)
{
    std::ostringstream oss;
    bool first = true;
    for (search.seek(1); ! search.isAtEnd(); search.seek(search.getDocId() + 1)) {
        if (!first) oss << ",";
        oss << search.getDocId();
        first = false;
    }
    return oss.str();
}

//-----------------------------------------------------------------------------

bool
verifyResult(const FakeResult &expect,
             Searchable &index,
             std::string fieldName,
             const Node &term)
{
    uint32_t fieldId = 0;
    FakeRequestContext requestContext;

    MatchDataLayout mdl;
    TermFieldHandle handle = mdl.allocTermField(fieldId);
    MatchData::UP match_data = mdl.createMatchData();

    FieldSpec field(fieldName, fieldId, handle);
    FieldSpecList fields;
    fields.add(field);

    Blueprint::UP result = index.createBlueprint(requestContext, fields, term);
    if (!EXPECT_TRUE(result.get() != 0)) {
        return false;
    }
    result->basic_plan(true, docid_limit);
    EXPECT_EQUAL(expect.inspect().size(), result->getState().estimate().estHits);
    EXPECT_EQUAL(expect.inspect().empty(), result->getState().estimate().empty);

    result->fetchPostings(ExecuteInfo::FULL);
    SearchIterator::UP search = result->createSearch(*match_data);
    if (!EXPECT_TRUE(search.get() != 0)) {
        return false;
    }
    TermFieldMatchData &tmd = *match_data->resolveTermField(handle);

    FakeResult actual;
    search->initFullRange();
    for (search->seek(1); !search->isAtEnd(); search->seek(search->getDocId() + 1)) {
        actual.doc(search->getDocId());
        search->unpack(search->getDocId());
        EXPECT_EQUAL(search->getDocId(), tmd.getDocId());
        FieldPositionsIterator p = tmd.getIterator();
        actual.len(p.getFieldLength());
        for (; p.valid(); p.next()) {
            actual.pos(p.getPosition());
        }
    }
    return EXPECT_EQUAL(expect, actual);
}

// tests basic usage; index some documents in docid order and perform
// some searches.
TEST_F("testIndexAndSearch", Fixture)
{
    f.put(1, makeDoc(f.repo, 1, "foo bar foo", "foo foo foo"));
    f.internalSyncCommit();
    f.put(2, makeDoc(f.repo, 2, "bar foo", "bar bar bar bar"));
    f.internalSyncCommit();

    // search for "foo" in "title"
    EXPECT_TRUE(verifyResult(FakeResult()
                            .doc(1).len(3).pos(0).pos(2)
                            .doc(2).len(2).pos(1),
                             f.index, title, makeTerm(foo)));

    // search for "bar" in "title"
    EXPECT_TRUE(verifyResult(FakeResult()
                            .doc(1).len(3).pos(1)
                            .doc(2).len(2).pos(0),
                            f.index, title, makeTerm(bar)));

    // search for "foo" in "body"
    EXPECT_TRUE(verifyResult(FakeResult()
                            .doc(1).len(3).pos(0).pos(1).pos(2),
                             f.index, body, makeTerm(foo)));

    // search for "bar" in "body"
    EXPECT_TRUE(verifyResult(FakeResult()
                            .doc(2).len(4).pos(0).pos(1).pos(2).pos(3),
                             f.index, body, makeTerm(bar)));

    // search for "bogus" in "title"
    EXPECT_TRUE(verifyResult(FakeResult(),
                            f.index, title, makeTerm("bogus")));

    // search for "foo" in "bogus"
    EXPECT_TRUE(verifyResult(FakeResult(),
                            f.index, "bogus", makeTerm(foo)));

    // search for "bar foo" in "title"
    EXPECT_TRUE(verifyResult(FakeResult()
                             .doc(1).len(3).pos(1)
                             .doc(2).len(2).pos(0),
                             f.index, title, *makePhrase(bar, foo)));

}

// tests index update behavior; remove/update and unordered docid
// indexing.
TEST_F("require that documents can be removed and updated", Fixture)
{
    // add unordered
    f.put(3, makeDoc(f.repo, 3, "foo foo foo"));
    f.internalSyncCommit();
    f.put(1, makeDoc(f.repo, 1, "foo"));
    f.internalSyncCommit();
    f.put(2, makeDoc(f.repo, 2, "foo foo"));
    f.internalSyncCommit();

    EXPECT_TRUE(verifyResult(FakeResult()
                            .doc(1).len(1).pos(0)
                            .doc(2).len(2).pos(0).pos(1)
                            .doc(3).len(3).pos(0).pos(1).pos(2),
                             f.index, title, makeTerm(foo)));

    // remove document
    f.remove(2);
    f.internalSyncCommit();

    EXPECT_TRUE(verifyResult(FakeResult()
                            .doc(1).len(1).pos(0)
                            .doc(3).len(3).pos(0).pos(1).pos(2),
                            f.index, title, makeTerm(foo)));

    // update document
    f.put(1, makeDoc(f.repo, 1, "bar foo foo"));
    f.internalSyncCommit();

    EXPECT_TRUE(verifyResult(FakeResult()
                            .doc(1).len(3).pos(1).pos(2)
                            .doc(3).len(3).pos(0).pos(1).pos(2),
                             f.index, title, makeTerm(foo)));
}


TEST_F("stress test, 4 readers", Fixture(4))
{
    f.stressTest(1000000);
}

TEST_F("stress test, 128 readers", Fixture(128))
{
    f.stressTest(1000000);
}

TEST_MAIN() { TEST_RUN_ALL(); }
