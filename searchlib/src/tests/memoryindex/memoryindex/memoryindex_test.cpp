// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/searchlib/memoryindex/memoryindex.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/index/docbuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/queryeval/booleanmatchiteratorwrapper.h>
#include <vespa/searchlib/queryeval/fake_search.h>
#include <vespa/searchlib/queryeval/fake_searchable.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/searchlib/common/sequencedtaskexecutor.h>
#include <vespa/searchlib/common/scheduletaskcallback.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

#include <vespa/log/log.h>
LOG_SETUP("memoryindex_test");

using document::Document;
using document::FieldValue;
using search::ScheduleTaskCallback;
using search::index::schema::DataType;
using vespalib::makeLambdaTask;
using search::query::Node;
using search::query::SimplePhrase;
using search::query::SimpleStringTerm;
using namespace search::fef;
using namespace search::index;
using namespace search::memoryindex;
using namespace search::queryeval;

//-----------------------------------------------------------------------------

struct Setup {
    Schema schema;
    Setup &field(const std::string &name) {
        schema.addIndexField(Schema::IndexField(name, DataType::STRING));
        return *this;
    }
};

//-----------------------------------------------------------------------------

struct Index {
    Schema       schema;
    vespalib::ThreadStackExecutor _executor;
    search::SequencedTaskExecutor _invertThreads;
    search::SequencedTaskExecutor _pushThreads;
    MemoryIndex  index;
    DocBuilder builder;
    uint32_t     docid;
    std::string  currentField;

    Index(const Setup &setup);
    ~Index();
    void closeField() {
        if (!currentField.empty()) {
            builder.endField();
            currentField.clear();
        }
    }
    Index &doc(uint32_t id) {
        docid = id;
        builder.startDocument(vespalib::make_string("doc::%u", id));
        return *this;
    }
    Index &field(const std::string &name) {
        closeField();
        builder.startIndexField(name);
        currentField = name;
        return *this;
    }
    Index &add(const std::string &token) {
        builder.addStr(token);
        return *this;
    }
    void internalSyncCommit() {
        vespalib::Gate gate;
        index.commit(std::make_shared<ScheduleTaskCallback>
                     (_executor,
                      makeLambdaTask([&]() { gate.countDown(); })));
        gate.await();
    }
    Document::UP commit() {
        closeField();
        Document::UP d = builder.endDocument();
        index.insertDocument(docid, *d);
        internalSyncCommit();
        return d;
    }
    Index &remove(uint32_t id) {
        index.removeDocument(id);
        internalSyncCommit();
        return *this;
    }

private:
    Index(const Index &index);
    Index &operator=(const Index &index);
};


Index::Index(const Setup &setup)
    : schema(setup.schema),
      _executor(1, 128 * 1024),
      _invertThreads(2),
      _pushThreads(2),
      index(schema, _invertThreads, _pushThreads),
      builder(schema),
      docid(1),
      currentField()
{
}
Index::~Index() {}
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

const std::string title("title");
const std::string body("body");
const std::string foo("foo");
const std::string bar("bar");

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
    EXPECT_EQUAL(expect.inspect().size(), result->getState().estimate().estHits);
    EXPECT_EQUAL(expect.inspect().empty(), result->getState().estimate().empty);

    result->fetchPostings(true);
    SearchIterator::UP search = result->createSearch(*match_data, true);
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

namespace {
SimpleStringTerm makeTerm(const std::string &term) {
    return SimpleStringTerm(term, "field", 0, search::query::Weight(0));
}

Node::UP makePhrase(const std::string &term1, const std::string &term2) {
    SimplePhrase * phrase = new SimplePhrase("field", 0, search::query::Weight(0));
    Node::UP node(phrase);
    phrase->append(Node::UP(new SimpleStringTerm(makeTerm(term1))));
    phrase->append(Node::UP(new SimpleStringTerm(makeTerm(term2))));
    return node;
}
}  // namespace

// tests basic usage; index some documents in docid order and perform
// some searches.
TEST("testIndexAndSearch")
{
    Index index(Setup().field(title).field(body));
    index.doc(1)
        .field(title).add(foo).add(bar).add(foo)
        .field(body).add(foo).add(foo).add(foo)
        .commit();
    index.doc(2)
        .field(title).add(bar).add(foo)
        .field(body).add(bar).add(bar).add(bar).add(bar)
        .commit();

    // search for "foo" in "title"
    EXPECT_TRUE(verifyResult(FakeResult()
                            .doc(1).len(3).pos(0).pos(2)
                            .doc(2).len(2).pos(1),
                            index.index, title, makeTerm(foo)));

    // search for "bar" in "title"
    EXPECT_TRUE(verifyResult(FakeResult()
                            .doc(1).len(3).pos(1)
                            .doc(2).len(2).pos(0),
                            index.index, title, makeTerm(bar)));

    // search for "foo" in "body"
    EXPECT_TRUE(verifyResult(FakeResult()
                            .doc(1).len(3).pos(0).pos(1).pos(2),
                            index.index, body, makeTerm(foo)));

    // search for "bar" in "body"
    EXPECT_TRUE(verifyResult(FakeResult()
                            .doc(2).len(4).pos(0).pos(1).pos(2).pos(3),
                            index.index, body, makeTerm(bar)));

    // search for "bogus" in "title"
    EXPECT_TRUE(verifyResult(FakeResult(),
                            index.index, title, makeTerm("bogus")));

    // search for "foo" in "bogus"
    EXPECT_TRUE(verifyResult(FakeResult(),
                            index.index, "bogus", makeTerm(foo)));

    // search for "bar foo" in "title"
    EXPECT_TRUE(verifyResult(FakeResult()
                            .doc(1).len(3).pos(1)
                            .doc(2).len(2).pos(0),
                            index.index, title, *makePhrase(bar, foo)));

}

// tests index update behavior; remove/update and unordered docid
// indexing.
TEST("require that documents can be removed and updated")
{
    Index index(Setup().field(title));

    // add unordered
    index.doc(3).field(title).add(foo).add(foo).add(foo).commit();
    Document::UP doc1 = index.doc(1).field(title).add(foo).commit();
    Document::UP doc2 = index.doc(2).field(title).add(foo).add(foo).commit();

    EXPECT_TRUE(verifyResult(FakeResult()
                            .doc(1).len(1).pos(0)
                            .doc(2).len(2).pos(0).pos(1)
                            .doc(3).len(3).pos(0).pos(1).pos(2),
                            index.index, title, makeTerm(foo)));

    // remove document
    index.remove(2);

    EXPECT_TRUE(verifyResult(FakeResult()
                            .doc(1).len(1).pos(0)
                            .doc(3).len(3).pos(0).pos(1).pos(2),
                            index.index, title, makeTerm(foo)));

    // update document
    index.doc(1).field(title).add(bar).add(foo).add(foo).commit();

    EXPECT_TRUE(verifyResult(FakeResult()
                            .doc(1).len(3).pos(1).pos(2)
                            .doc(3).len(3).pos(0).pos(1).pos(2),
                            index.index, title, makeTerm(foo)));
}

// test the fake field source here, to make sure it acts similar to
// the memory index field source.
TEST("testFakeSearchable")
{
    Index index(Setup().field(title).field(body));

    // setup fake field source with predefined results
    FakeSearchable fakeSource;
    fakeSource.addResult(title, foo,
                         FakeResult()
                         .doc(1).len(3).pos(0).pos(2)
                         .doc(2).len(2).pos(1));
    fakeSource.addResult(title, bar,
                         FakeResult()
                         .doc(1).len(3).pos(1)
                         .doc(2).len(2).pos(0));
    fakeSource.addResult(body, foo,
                         FakeResult()
                         .doc(1).len(3).pos(0).pos(1).pos(2));
    fakeSource.addResult(body, bar,
                         FakeResult()
                         .doc(2).len(4).pos(0).pos(1).pos(2).pos(3));

    // search for "foo" in "title"
    EXPECT_TRUE(verifyResult(FakeResult()
                            .doc(1).len(3).pos(0).pos(2)
                            .doc(2).len(2).pos(1),
                            fakeSource, title, makeTerm(foo)));

    // search for "bar" in "title"
    EXPECT_TRUE(verifyResult(FakeResult()
                            .doc(1).len(3).pos(1)
                            .doc(2).len(2).pos(0),
                            fakeSource, title, makeTerm(bar)));

    // search for "foo" in "body"
    EXPECT_TRUE(verifyResult(FakeResult()
                            .doc(1).len(3).pos(0).pos(1).pos(2),
                            fakeSource, body, makeTerm(foo)));

    // search for "bar" in "body"
    EXPECT_TRUE(verifyResult(FakeResult()
                            .doc(2).len(4).pos(0).pos(1).pos(2).pos(3),
                            fakeSource, body, makeTerm(bar)));

    // search for "bogus" in "title"
    EXPECT_TRUE(verifyResult(FakeResult(),
                            fakeSource, title, makeTerm("bogus")));

    // search for foo in "bogus"
    EXPECT_TRUE(verifyResult(FakeResult(),
                            fakeSource, "bogus", makeTerm(foo)));
}

TEST("requireThatFrozenIndexIgnoresUpdates")
{
    Index index(Setup().field(title));
    Document::UP doc1 = index.doc(1).field(title).add(foo).add(bar).commit();
    FakeResult ffr = FakeResult().doc(1).len(2).pos(0);
    EXPECT_TRUE(verifyResult(ffr, index.index, title, makeTerm(foo)));
    EXPECT_TRUE(!index.index.isFrozen());
    index.index.freeze();
    EXPECT_TRUE(index.index.isFrozen());
    index.doc(2).field(title).add(bar).add(foo).commit(); // not added
    EXPECT_TRUE(verifyResult(ffr, index.index, title, makeTerm(foo)));
    index.remove(1); // not removed
    EXPECT_TRUE(verifyResult(ffr, index.index, title, makeTerm(foo)));
}

TEST("requireThatNumDocsAndDocIdLimitIsReturned")
{
    Index index(Setup().field(title));
    EXPECT_EQUAL(0u, index.index.getNumDocs());
    EXPECT_EQUAL(1u, index.index.getDocIdLimit());
    Document::UP doc1 = index.doc(1).field(title).add(foo).commit();
    EXPECT_EQUAL(1u, index.index.getNumDocs());
    EXPECT_EQUAL(2u, index.index.getDocIdLimit());
    Document::UP doc4 = index.doc(4).field(title).add(foo).commit();
    EXPECT_EQUAL(2u, index.index.getNumDocs());
    EXPECT_EQUAL(5u, index.index.getDocIdLimit());
    Document::UP doc2 = index.doc(2).field(title).add(foo).commit();
    EXPECT_EQUAL(3u, index.index.getNumDocs());
    EXPECT_EQUAL(5u, index.index.getDocIdLimit());
    // re-add doc4
    index.doc(4).field(title).add(bar).commit();
    EXPECT_EQUAL(3u, index.index.getNumDocs());
    EXPECT_EQUAL(5u, index.index.getDocIdLimit());
    // remove doc2
    index.remove(2);
    EXPECT_EQUAL(2u, index.index.getNumDocs());
    EXPECT_EQUAL(5u, index.index.getDocIdLimit());
}

TEST("requireThatWeUnderstandTheMemoryFootprint")
{
    constexpr size_t BASE_SIZE = 188172u;
    {
        Setup setup;
        Index index(setup);
        EXPECT_EQUAL(0u, index.index.getStaticMemoryFootprint());
        EXPECT_EQUAL(index.index.getStaticMemoryFootprint(), index.index.getMemoryUsage().allocatedBytes());
    }
    {
        Index index(Setup().field("f1"));
        EXPECT_EQUAL(BASE_SIZE, index.index.getStaticMemoryFootprint());
        EXPECT_EQUAL(index.index.getStaticMemoryFootprint(), index.index.getMemoryUsage().allocatedBytes());
    }
    {
        Index index(Setup().field("f1").field("f2"));
        EXPECT_EQUAL(2 * BASE_SIZE, index.index.getStaticMemoryFootprint());
        EXPECT_EQUAL(index.index.getStaticMemoryFootprint(), index.index.getMemoryUsage().allocatedBytes());
    }
}

TEST("requireThatNumWordsIsReturned")
{
    Index index(Setup().field(title));
    EXPECT_EQUAL(0u, index.index.getNumWords());
    index.doc(1).field(title).add(foo).commit();
    EXPECT_EQUAL(1u, index.index.getNumWords());
    index.doc(2).field(title).add(foo).add(bar).add(body).commit();
    EXPECT_EQUAL(3u, index.index.getNumWords());
}

TEST("requireThatWeCanFakeBitVector")
{
    Index index(Setup().field(title));
    index.doc(1).field(title).add(foo).commit();
    index.doc(3).field(title).add(foo).commit();
    {
        uint32_t fieldId = 0;

        MatchDataLayout mdl;
        FakeRequestContext requestContext;
        TermFieldHandle handle = mdl.allocTermField(fieldId);
        MatchData::UP match_data = mdl.createMatchData();

        // filter field
        FieldSpec field(title, fieldId, handle, true);
        FieldSpecList fields;
        fields.add(field);

        Searchable &searchable = index.index;
        Blueprint::UP res = searchable.createBlueprint(requestContext, fields, makeTerm(foo));
        EXPECT_TRUE(res.get() != NULL);

        res->fetchPostings(true);
        SearchIterator::UP search = res->createSearch(*match_data, true);
        EXPECT_TRUE(search.get() != NULL);
        EXPECT_TRUE(dynamic_cast<BooleanMatchIteratorWrapper *>(search.get()) != NULL);
        search->initFullRange();
        EXPECT_EQUAL("1,3", toString(*search));
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
