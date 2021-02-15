// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/scheduletaskcallback.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/index/docbuilder.h>
#include <vespa/searchlib/index/i_field_length_inspector.h>
#include <vespa/searchlib/memoryindex/memory_index.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/queryeval/booleanmatchiteratorwrapper.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>
#include <vespa/searchlib/queryeval/fake_search.h>
#include <vespa/searchlib/queryeval/fake_searchable.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("memory_index_test");

using document::Document;
using document::FieldValue;
using search::ScheduleTaskCallback;
using search::index::schema::DataType;
using search::index::FieldLengthInfo;
using search::index::IFieldLengthInspector;
using vespalib::makeLambdaTask;
using search::query::Node;
using search::query::SimplePhrase;
using search::query::SimpleStringTerm;
using vespalib::ISequencedTaskExecutor;
using vespalib::SequencedTaskExecutor;
using namespace search::fef;
using namespace search::index;
using namespace search::memoryindex;
using namespace search::queryeval;

//-----------------------------------------------------------------------------

struct MySetup : public IFieldLengthInspector {
    Schema schema;
    std::map<vespalib::string, FieldLengthInfo> field_lengths;
    MySetup &field(const std::string &name) {
        schema.addIndexField(Schema::IndexField(name, DataType::STRING));
        return *this;
    }
    MySetup& field_length(const vespalib::string& field_name, const FieldLengthInfo& info) {
        field_lengths[field_name] = info;
        return *this;
    }
    FieldLengthInfo get_field_length_info(const vespalib::string& field_name) const override {
        auto itr = field_lengths.find(field_name);
        if (itr != field_lengths.end()) {
            return itr->second;
        }
        return FieldLengthInfo();
    }

};

//-----------------------------------------------------------------------------

struct Index {
    Schema       schema;
    vespalib::ThreadStackExecutor _executor;
    std::unique_ptr<ISequencedTaskExecutor> _invertThreads;
    std::unique_ptr<ISequencedTaskExecutor> _pushThreads;
    MemoryIndex  index;
    DocBuilder builder;
    uint32_t     docid;
    std::string  currentField;

    Index(const MySetup &setup);
    ~Index();
    void closeField() {
        if (!currentField.empty()) {
            builder.endField();
            currentField.clear();
        }
    }
    Index &doc(uint32_t id) {
        docid = id;
        builder.startDocument(vespalib::make_string("id:ns:searchdocument::%u", id));
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

VESPA_THREAD_STACK_TAG(invert_executor)
VESPA_THREAD_STACK_TAG(push_executor)

Index::Index(const MySetup &setup)
    : schema(setup.schema),
      _executor(1, 128_Ki),
      _invertThreads(SequencedTaskExecutor::create(invert_executor, 2)),
      _pushThreads(SequencedTaskExecutor::create(push_executor, 2)),
      index(schema, setup, *_invertThreads, *_pushThreads),
      builder(schema),
      docid(1),
      currentField()
{
}
Index::~Index() = default;
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
    bool valid_result = result.get() != 0;
    EXPECT_TRUE(valid_result);
    if (!valid_result) {
        return false;
    }
    EXPECT_EQ(expect.inspect().size(), result->getState().estimate().estHits);
    EXPECT_EQ(expect.inspect().empty(), result->getState().estimate().empty);

    result->fetchPostings(search::queryeval::ExecuteInfo::TRUE);
    SearchIterator::UP search = result->createSearch(*match_data, true);
    bool valid_search = search.get() != 0;
    EXPECT_TRUE(valid_search);
    if (!valid_search) {
        return false;
    }
    TermFieldMatchData &tmd = *match_data->resolveTermField(handle);

    FakeResult actual;
    search->initFullRange();
    for (search->seek(1); !search->isAtEnd(); search->seek(search->getDocId() + 1)) {
        actual.doc(search->getDocId());
        search->unpack(search->getDocId());
        EXPECT_EQ(search->getDocId(), tmd.getDocId());
        FieldPositionsIterator p = tmd.getIterator();
        actual.len(p.getFieldLength());
        for (; p.valid(); p.next()) {
            actual.pos(p.getPosition());
        }
    }
    EXPECT_EQ(expect, actual);
    return expect == actual;
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
TEST(MemoryIndexTest, test_index_and_search)
{
    Index index(MySetup().field(title).field(body));
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
TEST(MemoryIndexTest, require_that_documents_can_be_removed_and_updated)
{
    Index index(MySetup().field(title));

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
TEST(MemoryIndexTest, test_fake_searchable)
{
    Index index(MySetup().field(title).field(body));

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

TEST(MemoryIndexTest, require_that_frozen_index_ignores_updates)
{
    Index index(MySetup().field(title));
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

TEST(MemoryIndexTest, require_that_num_docs_and_doc_id_limit_is_returned)
{
    Index index(MySetup().field(title));
    EXPECT_EQ(0u, index.index.getNumDocs());
    EXPECT_EQ(1u, index.index.getDocIdLimit());
    Document::UP doc1 = index.doc(1).field(title).add(foo).commit();
    EXPECT_EQ(1u, index.index.getNumDocs());
    EXPECT_EQ(2u, index.index.getDocIdLimit());
    Document::UP doc4 = index.doc(4).field(title).add(foo).commit();
    EXPECT_EQ(2u, index.index.getNumDocs());
    EXPECT_EQ(5u, index.index.getDocIdLimit());
    Document::UP doc2 = index.doc(2).field(title).add(foo).commit();
    EXPECT_EQ(3u, index.index.getNumDocs());
    EXPECT_EQ(5u, index.index.getDocIdLimit());
    // re-add doc4
    index.doc(4).field(title).add(bar).commit();
    EXPECT_EQ(3u, index.index.getNumDocs());
    EXPECT_EQ(5u, index.index.getDocIdLimit());
    // remove doc2
    index.remove(2);
    EXPECT_EQ(2u, index.index.getNumDocs());
    EXPECT_EQ(5u, index.index.getDocIdLimit());
}

TEST(MemoryIndexTest, require_that_we_understand_the_memory_footprint)
{
    constexpr size_t BASE_SIZE = 188172u;
    {
        MySetup setup;
        Index index(setup);
        EXPECT_EQ(0u, index.index.getStaticMemoryFootprint());
        EXPECT_EQ(index.index.getStaticMemoryFootprint(), index.index.getMemoryUsage().allocatedBytes());
    }
    {
        Index index(MySetup().field("f1"));
        EXPECT_EQ(BASE_SIZE, index.index.getStaticMemoryFootprint());
        EXPECT_EQ(index.index.getStaticMemoryFootprint(), index.index.getMemoryUsage().allocatedBytes());
    }
    {
        Index index(MySetup().field("f1").field("f2"));
        EXPECT_EQ(2 * BASE_SIZE, index.index.getStaticMemoryFootprint());
        EXPECT_EQ(index.index.getStaticMemoryFootprint(), index.index.getMemoryUsage().allocatedBytes());
    }
}

TEST(MemoryIndexTest, require_that_num_words_is_returned)
{
    Index index(MySetup().field(title));
    EXPECT_EQ(0u, index.index.getNumWords());
    index.doc(1).field(title).add(foo).commit();
    EXPECT_EQ(1u, index.index.getNumWords());
    index.doc(2).field(title).add(foo).add(bar).add(body).commit();
    EXPECT_EQ(3u, index.index.getNumWords());
}

TEST(MemoryIndexTest, require_that_we_can_fake_bit_vector)
{
    Index index(MySetup().field(title));
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

        res->fetchPostings(search::queryeval::ExecuteInfo::TRUE);
        SearchIterator::UP search = res->createSearch(*match_data, true);
        EXPECT_TRUE(search.get() != NULL);
        EXPECT_TRUE(dynamic_cast<BooleanMatchIteratorWrapper *>(search.get()) != NULL);
        search->initFullRange();
        EXPECT_EQ("1,3", toString(*search));
    }
}

TEST(MemoryIndexTest, field_length_info_can_be_retrieved_per_field)
{
    Index index(MySetup().field(title).field(body)
                        .field_length("title", FieldLengthInfo(3, 5))
                        .field_length("body", FieldLengthInfo(7, 11)));

    EXPECT_EQ(3, index.index.get_field_length_info("title").get_average_field_length());
    EXPECT_EQ(5, index.index.get_field_length_info("title").get_num_samples());

    EXPECT_EQ(7, index.index.get_field_length_info("body").get_average_field_length());
    EXPECT_EQ(11, index.index.get_field_length_info("body").get_num_samples());

    EXPECT_EQ(0, index.index.get_field_length_info("na").get_average_field_length());
    EXPECT_EQ(0, index.index.get_field_length_info("na").get_num_samples());
}

GTEST_MAIN_RUN_ALL_TESTS()
