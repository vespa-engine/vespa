// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcorespi/index/fusionrunner.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/searchcore/proton/index/indexmanager.h>
#include <vespa/searchcore/proton/test/transport_helper.h>
#include <vespa/vespalib/util/isequencedtaskexecutor.h>
#include <vespa/searchlib/common/flush_token.h>
#include <vespa/searchlib/diskindex/diskindex.h>
#include <vespa/searchlib/diskindex/indexbuilder.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/test/doc_builder.h>
#include <vespa/searchlib/test/schema_builder.h>
#include <vespa/searchlib/test/string_field_builder.h>
#include <vespa/searchlib/memoryindex/memory_index.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/test/index/mock_field_length_inspector.h>
#include <vespa/searchlib/queryeval/fake_requestcontext.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/vespalib/util/gate.h>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <filesystem>
#include <set>

using document::Document;
using document::FieldValue;
using document::StringFieldValue;
using proton::ExecutorThreadingService;
using proton::index::IndexManager;
using search::FixedSourceSelector;
using search::TuneFileAttributes;
using search::TuneFileIndexManager;
using search::TuneFileIndexing;
using search::TuneFileSearch;
using search::diskindex::DiskIndex;
using search::diskindex::IndexBuilder;
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
using search::queryeval::ISourceSelector;
using search::queryeval::SearchIterator;
using search::test::DocBuilder;
using search::test::SchemaBuilder;
using search::test::StringFieldBuilder;
using searchcorespi::index::FusionRunner;
using searchcorespi::index::FusionSpec;
using std::set;
using std::string;

using namespace proton;

namespace {

class FusionRunnerTest : public ::testing::Test {
protected:
    std::unique_ptr<FusionRunner> _fusion_runner;
    FixedSourceSelector::UP _selector;
    FusionSpec _fusion_spec;
    DummyFileHeaderContext _fileHeaderContext;
    TransportAndExecutorService _service;
    IndexManager::MaintainerOperations _ops;

    FusionRunnerTest();
    ~FusionRunnerTest() override;
    void SetUp() override;
    void TearDown() override;

    void createIndex(const string &dir, uint32_t id, bool fusion = false);
    static void checkResults(uint32_t fusion_id, const uint32_t *ids, size_t size);

    void requireThatNoDiskIndexesGiveId0();
    void requireThatOneDiskIndexCausesCopy();
    void requireThatTwoDiskIndexesCauseFusion();
    void requireThatFusionCanRunOnMultipleDiskIndexes();
    void requireThatOldFusionIndexCanBePartOfNewFusion();
    void requireThatSelectorsCanBeRebased();
    void requireThatFusionCanBeStopped();
};

FusionRunnerTest::FusionRunnerTest()
    : ::testing::Test(),
      _fusion_runner(),
      _selector(),
      _fusion_spec(),
      _fileHeaderContext(),
      _service(1),
      _ops(_fileHeaderContext,TuneFileIndexManager(), 0, _service.write())
{ }

FusionRunnerTest::~FusionRunnerTest() = default;

const string base_dir = "fusion_test_data";
const string field_name = "field_name";
const string term = "foo";
const uint32_t disk_id[] = { 1, 2, 21, 42 };

auto add_fields = [](auto& header) { header.addField(field_name, document::DataType::T_STRING); };

Schema
getSchema()
{
    DocBuilder db(add_fields);
    return SchemaBuilder(db).add_all_indexes().build();
}

void
FusionRunnerTest::SetUp()
{
    std::filesystem::remove_all(std::filesystem::path(base_dir));
    _fusion_runner = std::make_unique<FusionRunner>(base_dir, getSchema(), TuneFileAttributes(), _fileHeaderContext);
    const string selector_base = base_dir + "/index.flush.0/selector";
    _selector = std::make_unique<FixedSourceSelector>(0, selector_base);
    _fusion_spec = FusionSpec();
}

void
FusionRunnerTest::TearDown()
{
    std::filesystem::remove_all(std::filesystem::path(base_dir));
    _selector.reset();
}

Document::UP
buildDocument(DocBuilder & doc_builder, int id, const string &word)
{
    vespalib::asciistream ost;
    ost << "id:ns:searchdocument::" << id;
    auto doc = doc_builder.make_document(ost.str());
    doc->setValue(field_name, StringFieldBuilder(doc_builder).word(word).build());
    return doc;
}

void
addDocument(DocBuilder & doc_builder, MemoryIndex &index, ISourceSelector &selector,
            uint8_t index_id, uint32_t docid, const string &word)
{
    Document::UP doc = buildDocument(doc_builder, docid, word);
    index.insertDocument(docid, *doc, {});
    vespalib::Gate gate;
    index.commit(std::make_shared<vespalib::GateCallback>(gate));
    selector.setSource(docid, index_id);
    gate.await();
}

void
FusionRunnerTest::createIndex(const string &dir, uint32_t id, bool fusion)
{
    std::filesystem::create_directory(std::filesystem::path(dir));
    vespalib::asciistream ost;
    if (fusion) {
        ost << dir << "/index.fusion." << id;
        _fusion_spec.last_fusion_id = id;
    } else {
        ost << dir << "/index.flush." << id;
        _fusion_spec.flush_ids.push_back(id);
    }
    std::string_view index_dir = ost.str();
    _selector->setDefaultSource(id - _selector->getBaseId());

    DocBuilder doc_builder(add_fields);
    auto schema = SchemaBuilder(doc_builder).add_all_indexes().build();
    MemoryIndex memory_index(schema, MockFieldLengthInspector(),
                             _service.write().field_writer(),
                             _service.write().field_writer());
    addDocument(doc_builder, memory_index, *_selector, id, id + 0, term);
    addDocument(doc_builder, memory_index, *_selector, id, id + 1, "bar");
    addDocument(doc_builder, memory_index, *_selector, id, id + 2, "baz");
    addDocument(doc_builder, memory_index, *_selector, id, id + 3, "qux");

    const uint32_t docIdLimit = std::min(memory_index.getDocIdLimit(), _selector->getDocIdLimit());
    TuneFileAttributes tuneFileAttributes;
    {
        TuneFileIndexing tuneFileIndexing;
        MockFieldLengthInspector fieldLengthInspector;
        IndexBuilder index_builder(schema, index_dir, docIdLimit, memory_index.getNumWords(), fieldLengthInspector,
                                   tuneFileIndexing, _fileHeaderContext);
        memory_index.dump(index_builder);
    }

    _selector->extractSaveInfo(index_dir + "/selector")->save(tuneFileAttributes, _fileHeaderContext);
}

set<uint32_t>
readFusionIds(const string &dir)
{
    set<uint32_t> ids;
    const vespalib::string prefix("index.fusion.");
    std::filesystem::directory_iterator dir_scan(dir);
    for (auto& entry : dir_scan) {
        if (entry.is_directory() && entry.path().filename().string().find(prefix) == 0) {
            auto idString = entry.path().filename().string().substr(prefix.size());
            vespalib::asciistream ist(idString);
            uint32_t id;
            ist >> id;
            ids.insert(id);
        }
    }
    return ids;
}

vespalib::string
getFusionIndexName(uint32_t fusion_id)
{
   vespalib::asciistream ost;
   ost << base_dir << "/index.fusion." << fusion_id;
   return ost.str();
}

void
FusionRunnerTest::checkResults(uint32_t fusion_id, const uint32_t *ids, size_t size)
{
    FakeRequestContext requestContext;
    DiskIndex disk_index(getFusionIndexName(fusion_id));
    ASSERT_TRUE(disk_index.setup(TuneFileSearch()));
    uint32_t fieldId = 0;

    MatchDataLayout mdl;
    TermFieldHandle handle = mdl.allocTermField(fieldId);
    MatchData::UP match_data = mdl.createMatchData();

    FieldSpec field(field_name, fieldId, handle);
    FieldSpecList fields;
    fields.add(field);

    search::queryeval::Searchable &searchable = disk_index;
    SimpleStringTerm node(term, field_name, fieldId, search::query::Weight(0));
    Blueprint::UP blueprint = searchable.createBlueprint(requestContext, fields, node);
    blueprint->basic_plan(true, 1000);
    blueprint->fetchPostings(search::queryeval::ExecuteInfo::FULL);
    SearchIterator::UP search = blueprint->createSearch(*match_data);
    search->initFullRange();
    for (size_t i = 0; i < size; ++i) {
        EXPECT_TRUE(search->seek(ids[i]));
    }
}

TEST_F(FusionRunnerTest, require_that_no_disk_indexes_give_id_0)
{
    uint32_t fusion_id = _fusion_runner->fuse(_fusion_spec, 0u, _ops, std::make_shared<search::FlushToken>());
    EXPECT_EQ(0u, fusion_id);
}

TEST_F(FusionRunnerTest, rquire_that_one_disk_index_causes_copy)
{
    createIndex(base_dir, disk_id[0]);
    uint32_t fusion_id = _fusion_runner->fuse(_fusion_spec, 0u, _ops, std::make_shared<search::FlushToken>());
    EXPECT_EQ(disk_id[0], fusion_id);
    set<uint32_t> fusion_ids = readFusionIds(base_dir);
    ASSERT_TRUE(!fusion_ids.empty());
    EXPECT_EQ(1u, fusion_ids.size());
    EXPECT_EQ(fusion_id, *fusion_ids.begin());

    checkResults(fusion_id, disk_id, 1);
}

TEST_F(FusionRunnerTest, require_that_two_disk_indexes_cause_fusion)
{
    createIndex(base_dir, disk_id[0]);
    createIndex(base_dir, disk_id[1]);
    uint32_t fusion_id = _fusion_runner->fuse(_fusion_spec, 0u, _ops, std::make_shared<search::FlushToken>());
    EXPECT_EQ(disk_id[1], fusion_id);
    set<uint32_t> fusion_ids = readFusionIds(base_dir);
    ASSERT_TRUE(!fusion_ids.empty());
    EXPECT_EQ(1u, fusion_ids.size());
    EXPECT_EQ(fusion_id, *fusion_ids.begin());

    checkResults(fusion_id, disk_id, 2);
}

TEST_F(FusionRunnerTest, require_that_fusion_can_run_on_multiple_disk_indexes)
{
    createIndex(base_dir, disk_id[0]);
    createIndex(base_dir, disk_id[1]);
    createIndex(base_dir, disk_id[2]);
    createIndex(base_dir, disk_id[3]);
    uint32_t fusion_id = _fusion_runner->fuse(_fusion_spec, 0u, _ops, std::make_shared<search::FlushToken>());
    EXPECT_EQ(disk_id[3], fusion_id);
    set<uint32_t> fusion_ids = readFusionIds(base_dir);
    ASSERT_TRUE(!fusion_ids.empty());
    EXPECT_EQ(1u, fusion_ids.size());
    EXPECT_EQ(fusion_id, *fusion_ids.begin());

    checkResults(fusion_id, disk_id, 4);
}

TEST_F(FusionRunnerTest, require_that_old_fusion_index_can_be_part_of_new_fusion)
{
    createIndex(base_dir, disk_id[0], true);
    createIndex(base_dir, disk_id[1]);
    uint32_t fusion_id = _fusion_runner->fuse(_fusion_spec, 0u, _ops, std::make_shared<search::FlushToken>());
    EXPECT_EQ(disk_id[1], fusion_id);
    set<uint32_t> fusion_ids = readFusionIds(base_dir);
    ASSERT_TRUE(!fusion_ids.empty());
    EXPECT_EQ(2u, fusion_ids.size());
    EXPECT_EQ(disk_id[0], *fusion_ids.begin());
    EXPECT_EQ(fusion_id, *(++fusion_ids.begin()));

    checkResults(fusion_id, disk_id, 2);
}

TEST_F(FusionRunnerTest, require_that_selectors_can_be_rebased)
{
    createIndex(base_dir, disk_id[0]);
    createIndex(base_dir, disk_id[1]);
    uint32_t fusion_id = _fusion_runner->fuse(_fusion_spec, 0u, _ops, std::make_shared<search::FlushToken>());

    _fusion_spec.flush_ids.clear();
    _fusion_spec.last_fusion_id = fusion_id;
    createIndex(base_dir, disk_id[2]);
    fusion_id = _fusion_runner->fuse(_fusion_spec, 0u, _ops, std::make_shared<search::FlushToken>());

    checkResults(fusion_id, disk_id, 3);
}

TEST_F(FusionRunnerTest, require_that_fusion_can_be_stopped)
{
    createIndex(base_dir, disk_id[0]);
    createIndex(base_dir, disk_id[1]);
    auto flush_token = std::make_shared<search::FlushToken>();
    flush_token->request_stop();
    uint32_t fusion_id = _fusion_runner->fuse(_fusion_spec, 0u, _ops, flush_token);
    EXPECT_EQ(0u, fusion_id);
}

}  // namespace

int
main(int argc, char* argv[])
{
    ::testing::InitGoogleTest(&argc, argv);
    if (argc > 0) {
        DummyFileHeaderContext::setCreator(argv[0]);
    }
    return RUN_ALL_TESTS();
}
