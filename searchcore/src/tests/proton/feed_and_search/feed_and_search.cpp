// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/datatype/datatype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/fieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/searchlib/common/documentsummary.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>
#include <vespa/searchlib/common/flush_token.h>
#include <vespa/searchlib/diskindex/diskindex.h>
#include <vespa/searchlib/diskindex/fusion.h>
#include <vespa/searchlib/diskindex/indexbuilder.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/memoryindex/memory_index.h>
#include <vespa/searchlib/test/doc_builder.h>
#include <vespa/searchlib/test/schema_builder.h>
#include <vespa/searchlib/test/string_field_builder.h>
#include <vespa/searchlib/test/index/mock_field_length_inspector.h>
#include <vespa/searchlib/query/base.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/gate.h>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP("feed_and_search_test");

using document::DataType;
using document::Document;
using document::FieldValue;
using document::StringFieldValue;
using search::DocumentIdT;
using search::FlushToken;
using search::TuneFileIndexing;
using search::TuneFileSearch;
using search::diskindex::DiskIndex;
using search::diskindex::IndexBuilder;
using search::diskindex::SelectorArray;
using search::docsummary::DocumentSummary;
using search::fef::FieldPositionsIterator;
using search::fef::MatchData;
using search::fef::MatchDataLayout;
using search::fef::TermFieldHandle;
using search::fef::TermFieldMatchData;
using search::index::DummyFileHeaderContext;
using search::index::Schema;
using search::index::test::MockFieldLengthInspector;
using search::memoryindex::MemoryIndex;
using search::query::SimpleStringTerm;
using search::queryeval::Blueprint;
using search::queryeval::FakeRequestContext;
using search::queryeval::FieldSpec;
using search::queryeval::FieldSpecList;
using search::queryeval::SearchIterator;
using search::queryeval::Searchable;
using search::test::DocBuilder;
using search::test::SchemaBuilder;
using search::test::StringFieldBuilder;
using std::ostringstream;
using vespalib::string;

namespace {

void commit_memory_index_and_wait(MemoryIndex &memory_index)
{
    vespalib::Gate gate;
    memory_index.commit(std::make_shared<vespalib::GateCallback>(gate));
    gate.await();
}

class Test : public vespalib::TestApp {
    const char *current_state;
    void DumpState(bool) {
      fprintf(stderr, "%s: ERROR: in %s\n", __FILE__, current_state);
    }

    void requireThatMemoryIndexCanBeDumpedAndSearched();

    void testSearch(Searchable &source,
                    const string &term, uint32_t doc_id);

public:
    int Main() override;
};

#define TEST_CALL(func) \
    current_state = #func; \
    func();

int
Test::Main()
{
    TEST_INIT("feed_and_search_test");

    if (_argc > 0) {
        DummyFileHeaderContext::setCreator(_argv[0]);
    }
    TEST_CALL(requireThatMemoryIndexCanBeDumpedAndSearched);

    TEST_DONE();
}

const string field_name = "string_field";
const string noise = "noise";
const string word1 = "foo";
const string word2 = "bar";
const DocumentIdT doc_id1 = 1;
const DocumentIdT doc_id2 = 2;

Document::UP buildDocument(DocBuilder & doc_builder, int id,
                           const string &word) {
    ostringstream ost;
    ost << "id:ns:searchdocument::" << id;
    auto doc = doc_builder.make_document(ost.str());
    doc->setValue(field_name, StringFieldBuilder(doc_builder).word(noise).space().word(word).build());
    return doc;
}

// Performs a search using a Searchable.
void Test::testSearch(Searchable &source,
                      const string &term, uint32_t doc_id)
{
    FakeRequestContext requestContext;
    uint32_t fieldId = 0;
    MatchDataLayout mdl;
    TermFieldHandle handle = mdl.allocTermField(fieldId);
    MatchData::UP match_data = mdl.createMatchData();

    SimpleStringTerm node(term, field_name, 0, search::query::Weight(0));
    Blueprint::UP result = source.createBlueprint(requestContext,
            FieldSpecList().add(FieldSpec(field_name, 0, handle)), node);
    result->fetchPostings(search::queryeval::ExecuteInfo::TRUE);
    SearchIterator::UP search_iterator =
        result->createSearch(*match_data, true);
    search_iterator->initFullRange();
    ASSERT_TRUE(search_iterator.get());
    ASSERT_TRUE(search_iterator->seek(doc_id));
    EXPECT_EQUAL(doc_id, search_iterator->getDocId());
    search_iterator->unpack(doc_id);
    FieldPositionsIterator it =
        match_data->resolveTermField(handle)->getIterator();
    ASSERT_TRUE(it.valid());
    EXPECT_EQUAL(1u, it.size());
    EXPECT_EQUAL(1u, it.getPosition());  // All hits are at pos 1 in this index

    EXPECT_TRUE(!search_iterator->seek(doc_id + 1));
    EXPECT_TRUE(search_iterator->isAtEnd());
}

VESPA_THREAD_STACK_TAG(invert_executor)
VESPA_THREAD_STACK_TAG(write_executor)
// Creates a memory index, inserts documents, performs a few
// searches, dumps the index to disk, and performs the searches
// again.
void Test::requireThatMemoryIndexCanBeDumpedAndSearched() {
    vespalib::ThreadStackExecutor sharedExecutor(2, 0x10000);
    auto indexFieldInverter = vespalib::SequencedTaskExecutor::create(invert_executor, 2);
    auto indexFieldWriter = vespalib::SequencedTaskExecutor::create(write_executor, 2);
    DocBuilder doc_builder([](auto& header) { header.addField(field_name, DataType::T_STRING); });
    auto schema = SchemaBuilder(doc_builder).add_all_indexes().build();
    MemoryIndex memory_index(schema, MockFieldLengthInspector(), *indexFieldInverter, *indexFieldWriter);

    Document::UP doc = buildDocument(doc_builder, doc_id1, word1);
    memory_index.insertDocument(doc_id1, *doc, {});

    auto doc2 = buildDocument(doc_builder, doc_id2, word2);
    memory_index.insertDocument(doc_id2, *doc2, {});
    commit_memory_index_and_wait(memory_index);

    testSearch(memory_index, word1, doc_id1);
    testSearch(memory_index, word2, doc_id2);

    const string index_dir = "test_index";
    IndexBuilder index_builder(schema);
    index_builder.setPrefix(index_dir);
    const uint32_t docIdLimit = memory_index.getDocIdLimit();
    const uint64_t num_words = memory_index.getNumWords();
    search::TuneFileIndexing tuneFileIndexing;
    DummyFileHeaderContext fileHeaderContext;
    index_builder.open(docIdLimit, num_words, MockFieldLengthInspector(), tuneFileIndexing, fileHeaderContext);
    memory_index.dump(index_builder);
    index_builder.close();

    // Fusion test.  Keep all documents to get an "indentical" copy.
    const string index_dir2 = "test_index2";
    std::vector<string> fusionInputs;
    fusionInputs.push_back(index_dir);
    uint32_t fusionDocIdLimit = 0;
    using Fusion = search::diskindex::Fusion;
    bool fret1 = DocumentSummary::readDocIdLimit(index_dir, fusionDocIdLimit);
    ASSERT_TRUE(fret1);
    SelectorArray selector(fusionDocIdLimit, 0);
    {
        Fusion fusion(schema,
                      index_dir2,
                      fusionInputs,
                      selector,
                      tuneFileIndexing,
                      fileHeaderContext);
        bool fret2 = fusion.merge(sharedExecutor, std::make_shared<FlushToken>());
        ASSERT_TRUE(fret2);
    }

    // Fusion test with all docs removed in output (doesn't affect word list)
    const string index_dir3 = "test_index3";
    fusionInputs.clear();
    fusionInputs.push_back(index_dir);
    fusionDocIdLimit = 0;
    bool fret3 = DocumentSummary::readDocIdLimit(index_dir, fusionDocIdLimit);
    ASSERT_TRUE(fret3);
    SelectorArray selector2(fusionDocIdLimit, 1);
    {
        Fusion fusion(schema,
                      index_dir3,
                      fusionInputs,
                      selector2,
                      tuneFileIndexing,
                      fileHeaderContext);
        bool fret4 = fusion.merge(sharedExecutor, std::make_shared<FlushToken>());
        ASSERT_TRUE(fret4);
    }

    // Fusion test with all docs removed in input (affects word list)
    const string index_dir4 = "test_index4";
    fusionInputs.clear();
    fusionInputs.push_back(index_dir3);
    fusionDocIdLimit = 0;
    bool fret5 = DocumentSummary::readDocIdLimit(index_dir3, fusionDocIdLimit);
    ASSERT_TRUE(fret5);
    SelectorArray selector3(fusionDocIdLimit, 0);
    {
        Fusion fusion(schema,
                      index_dir4,
                      fusionInputs,
                      selector3,
                      tuneFileIndexing,
                      fileHeaderContext);
        bool fret6 = fusion.merge(sharedExecutor,
                                  std::make_shared<FlushToken>());
        ASSERT_TRUE(fret6);
    }

    DiskIndex disk_index(index_dir);
    ASSERT_TRUE(disk_index.setup(TuneFileSearch()));
    testSearch(disk_index, word1, doc_id1);
    testSearch(disk_index, word2, doc_id2);
    DiskIndex disk_index2(index_dir2);
    ASSERT_TRUE(disk_index2.setup(TuneFileSearch()));
    testSearch(disk_index2, word1, doc_id1);
    testSearch(disk_index2, word2, doc_id2);
}
}  // namespace

TEST_APPHOOK(Test);
