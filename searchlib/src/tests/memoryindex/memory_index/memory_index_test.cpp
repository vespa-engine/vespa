// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/repo/newconfigbuilder.h>
#include <vespa/searchlib/common/scheduletaskcallback.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>
#include <vespa/searchlib/index/i_field_length_inspector.h>
#include <vespa/searchlib/memoryindex/memory_index.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/booleanmatchiteratorwrapper.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>
#include <vespa/searchlib/queryeval/fake_searchable.h>
#include <vespa/searchlib/queryeval/leaf_blueprints.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/queryeval/simple_phrase_blueprint.h>
#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/searchlib/test/doc_builder.h>
#include <vespa/searchlib/test/schema_builder.h>
#include <vespa/searchlib/test/string_field_builder.h>
#include <vespa/searchlib/util/index_stats.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

#include <vespa/log/log.h>
LOG_SETUP("memory_index_test");

using document::DataType;
using document::Document;
using document::FieldValue;
using search::FieldIndexStats;
using search::IndexStats;
using search::ScheduleTaskCallback;
using search::index::FieldLengthInfo;
using search::index::IFieldLengthInspector;
using search::query::Node;
using search::query::SimplePhrase;
using search::query::SimpleStringTerm;
using search::test::DocBuilder;
using search::test::SchemaBuilder;
using search::test::StringFieldBuilder;
using vespalib::ISequencedTaskExecutor;
using vespalib::SequencedTaskExecutor;
using vespalib::Slime;
using vespalib::makeLambdaTask;
using vespalib::slime::JsonFormat;
using vespalib::slime::SlimeInserter;

using namespace search::fef;
using namespace search::index;
using namespace search::memoryindex;
using namespace search::queryeval;

//-----------------------------------------------------------------------------

struct MySetup : public IFieldLengthInspector {
    std::vector<std::string> fields;
    std::map<std::string, FieldLengthInfo> field_lengths;
    MySetup();
    ~MySetup() override;
    MySetup &field(const std::string &name) {
        fields.emplace_back(name);
        return *this;
    }
    MySetup& field_length(const std::string& field_name, const FieldLengthInfo& info) {
        field_lengths[field_name] = info;
        return *this;
    }
    FieldLengthInfo get_field_length_info(const std::string& field_name) const override {
        auto itr = field_lengths.find(field_name);
        if (itr != field_lengths.end()) {
            return itr->second;
        }
        return FieldLengthInfo();
    }

    void add_fields(document::new_config_builder::NewConfigBuilder& builder,
                    document::new_config_builder::NewDocTypeRep& doc) const {
        for (auto& field : fields) {
            doc.addField(field, builder.stringTypeRef());
        }
    }

    Schema make_all_index_schema() const {
        DocBuilder db([this](auto& builder, auto& doc) noexcept { add_fields(builder, doc); });
        return SchemaBuilder(db).add_all_indexes().build();
    }

};

MySetup::MySetup() = default;
MySetup::~MySetup() = default;

//-----------------------------------------------------------------------------

struct Index {
    vespalib::ThreadStackExecutor _executor;
    std::unique_ptr<ISequencedTaskExecutor> _invertThreads;
    std::unique_ptr<ISequencedTaskExecutor> _pushThreads;
    MemoryIndex  index;
    DocBuilder builder;
    StringFieldBuilder sfb;
    std::unique_ptr<Document> builder_doc;
    uint32_t     docid;
    std::string  currentField;
    bool         add_space;

    Index(const MySetup &setup);
    ~Index();
    void closeField() {
        if (!currentField.empty()) {
            builder_doc->setValue(currentField, sfb.build());
            currentField.clear();
        }
    }
    Index &doc(uint32_t id) {
        docid = id;
        builder_doc = builder.make_document(vespalib::make_string("id:ns:searchdocument::%u", id));
        return *this;
    }
    Index &field(const std::string &name) {
        closeField();
        currentField = name;
        add_space = false;
        return *this;
    }
    Index &add(const std::string &token) {
        if (add_space) {
            sfb.space();
        }
        add_space = true;
        sfb.word(token);
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
        Document::UP d = std::move(builder_doc);
        index.insertDocument(docid, *d, {});
        internalSyncCommit();
        return d;
    }
    Index &remove(uint32_t id) {
        std::vector<uint32_t> lids;
        lids.push_back(id);
        index.removeDocuments(std::move(lids));
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
    : _executor(1),
      _invertThreads(SequencedTaskExecutor::create(invert_executor, 2)),
      _pushThreads(SequencedTaskExecutor::create(push_executor, 2)),
      index(setup.make_all_index_schema(), setup, *_invertThreads, *_pushThreads),
      builder([&setup](auto& b, auto& doc) noexcept { setup.add_fields(b, doc); }),
      sfb(builder),
      builder_doc(),
      docid(1),
      currentField(),
      add_space(false)
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

    FieldSpec field(fieldName, fieldId, handle);
    FieldSpecList fields;
    fields.add(field);

    auto result = index.createBlueprint(requestContext, fields, term, mdl);
    bool valid_result = result.get() != nullptr;
    EXPECT_TRUE(valid_result);
    if (!valid_result) {
        return false;
    }
    EXPECT_EQ(expect.inspect().size(), result->getState().estimate().estHits);
    EXPECT_EQ(expect.inspect().empty(), result->getState().estimate().empty);

    result->basic_plan(true, 100);
    result->fetchPostings(search::queryeval::ExecuteInfo::FULL);
    MatchData::UP match_data = mdl.createMatchData();
    SearchIterator::UP search = result->createSearch(*match_data);
    bool valid_search = search.get() != nullptr;
    EXPECT_TRUE(valid_search);
    if (!valid_search) {
        return false;
    }
    TermFieldMatchData &tmd = *match_data->resolveTermField(handle);

    FakeResult actual;
    SimpleResult exp_simple;
    search->initFullRange();
    for (search->seek(1); !search->isAtEnd(); search->seek(search->getDocId() + 1)) {
        exp_simple.addHit(search->getDocId());
        actual.doc(search->getDocId());
        search->unpack(search->getDocId());
        EXPECT_TRUE(tmd.has_ranking_data(search->getDocId()));
        FieldPositionsIterator p = tmd.getIterator();
        actual.len(p.getFieldLength());
        for (; p.valid(); p.next()) {
            actual.pos(p.getPosition());
        }
    }
    bool success = true;
    EXPECT_EQ(expect, actual) << (success = false, "");
    using FilterConstraint = search::queryeval::Blueprint::FilterConstraint;
    for (auto constraint : { FilterConstraint::LOWER_BOUND, FilterConstraint::UPPER_BOUND }) {
        constexpr uint32_t docid_limit = 10u;
        auto filter_search = result->createFilterSearch(constraint);
        auto act_simple = SimpleResult().search(*filter_search, docid_limit);
        if (constraint == FilterConstraint::LOWER_BOUND) {
            EXPECT_TRUE(exp_simple.contains(act_simple)) << (success = false, "");
        }
        if (constraint == FilterConstraint::UPPER_BOUND) {
            EXPECT_TRUE(act_simple.contains(exp_simple)) << (success = false, "");
        }
        if (dynamic_cast<FakeBlueprint*>(result.get()) == nullptr &&
            dynamic_cast<SimplePhraseBlueprint*>(result.get()) == nullptr) {
            EXPECT_EQ(exp_simple, act_simple) << (success = false, "");
        }
    }
    return success;
}

namespace {
SimpleStringTerm makeTerm(const std::string &term) {
    return SimpleStringTerm(term, "field", 0, search::query::Weight(0));
}

Node::UP makePhrase(const std::string &term1, const std::string &term2) {
    auto phrase = std::make_unique<SimplePhrase>("field", 0, search::query::Weight(0));
    phrase->append(std::make_unique<SimpleStringTerm>(makeTerm(term1)));
    phrase->append(std::make_unique<SimpleStringTerm>(makeTerm(term2)));
    return phrase;
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

namespace {

FieldIndexStats get_field_stats(const IndexStats &stats, const std::string& field_name)
{
    auto itr = stats.get_field_stats().find(field_name);
    return itr == stats.get_field_stats().end() ? FieldIndexStats() : itr->second;
}

}
TEST(MemoryIndexTest, require_that_we_understand_the_memory_footprint)
{
    constexpr size_t BASE_ALLOCATED = 360936u;
    constexpr size_t BASE_USED = 150676u;
    {
        MySetup setup;
        Index index(setup);
        EXPECT_EQ(0u, index.index.getStaticMemoryFootprint());
        EXPECT_EQ(index.index.getStaticMemoryFootprint(), index.index.getMemoryUsage().allocatedBytes());
        EXPECT_EQ(0u, index.index.getMemoryUsage().usedBytes());
    }
    {
        Index index(MySetup().field("f1"));
        EXPECT_EQ(BASE_ALLOCATED, index.index.getStaticMemoryFootprint());
        EXPECT_EQ(index.index.getStaticMemoryFootprint(), index.index.getMemoryUsage().allocatedBytes());
        EXPECT_EQ(BASE_USED, index.index.getMemoryUsage().usedBytes());
    }
    {
        Index index(MySetup().field("f1").field("f2"));
        EXPECT_EQ(2 * BASE_ALLOCATED, index.index.getStaticMemoryFootprint());
        EXPECT_EQ(index.index.getStaticMemoryFootprint(), index.index.getMemoryUsage().allocatedBytes());
        EXPECT_EQ(2 * BASE_USED, index.index.getMemoryUsage().usedBytes());
        EXPECT_EQ(2 * BASE_USED, index.index.get_stats().memoryUsage().usedBytes());
        EXPECT_EQ(BASE_USED, get_field_stats(index.index.get_stats(), "f1").memory_usage().usedBytes());
        EXPECT_EQ(BASE_USED, get_field_stats(index.index.get_stats(), "f2").memory_usage().usedBytes());
        EXPECT_EQ(0, get_field_stats(index.index.get_stats(), "f3").memory_usage().usedBytes());
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

        // filter field
        FieldSpec field(title, fieldId, handle, true);
        FieldSpecList fields;
        fields.add(field);

        Searchable &searchable = index.index;
        auto res = searchable.createBlueprint(requestContext, fields, makeTerm(foo), mdl);
        EXPECT_TRUE(res);

        res->basic_plan(true, 100);
        res->fetchPostings(search::queryeval::ExecuteInfo::FULL);
        MatchData::UP match_data = mdl.createMatchData();
        SearchIterator::UP search = res->createSearch(*match_data);
        EXPECT_TRUE(search);
        EXPECT_TRUE(dynamic_cast<BooleanMatchIteratorWrapper *>(search.get()) != nullptr);
        search->initFullRange();
        EXPECT_EQ("1,3", toString(*search));
    }
}

TEST(MemoryIndexTest, field_length_info_can_be_retrieved_per_field)
{
    Index index(MySetup().field(title).field(body)
                        .field_length("title", FieldLengthInfo(3.0, 3.0, 5))
                        .field_length("body", FieldLengthInfo(7.0, 7.0, 11)));

    EXPECT_EQ(3, index.index.get_field_length_info("title").get_average_field_length());
    EXPECT_EQ(5, index.index.get_field_length_info("title").get_num_samples());

    EXPECT_EQ(7, index.index.get_field_length_info("body").get_average_field_length());
    EXPECT_EQ(11, index.index.get_field_length_info("body").get_num_samples());

    EXPECT_EQ(0, index.index.get_field_length_info("na").get_average_field_length());
    EXPECT_EQ(0, index.index.get_field_length_info("na").get_num_samples());
}

TEST(MemoryIndexTest, write_context_state_as_slime)
{
    Index index(MySetup().field(title).field(body));
    Slime act;
    SlimeInserter inserter(act);
    index.index.insert_write_context_state(inserter.insertObject());
    Slime exp;
    JsonFormat::decode("{\"invert\": [{\"executor_id\": 0, \"fields\": [\"body\"]},"
                                     "{\"executor_id\": 1, \"fields\": [\"title\"]}],"
                        "\"push\": [{\"executor_id\": 0, \"fields\": [\"body\"]},"
                                   "{\"executor_id\": 1, \"fields\": [\"title\"]}]}", exp);
    EXPECT_EQ(exp, act);
}

GTEST_MAIN_RUN_ALL_TESTS()
