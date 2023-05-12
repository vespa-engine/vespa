// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/fastos/file.h>
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

#define TEST_CALL(func) \
    setUp(); \
    func; \
    tearDown()

class Test : public vespalib::TestApp {
    std::unique_ptr<FusionRunner> _fusion_runner;
    FixedSourceSelector::UP _selector;
    FusionSpec _fusion_spec;
    DummyFileHeaderContext _fileHeaderContext;
    TransportAndExecutorService _service;
    IndexManager::MaintainerOperations _ops;

    void setUp();
    void tearDown();

    void createIndex(const string &dir, uint32_t id, bool fusion = false);
    void checkResults(uint32_t fusion_id, const uint32_t *ids, size_t size);

    void requireThatNoDiskIndexesGiveId0();
    void requireThatOneDiskIndexCausesCopy();
    void requireThatTwoDiskIndexesCauseFusion();
    void requireThatFusionCanRunOnMultipleDiskIndexes();
    void requireThatOldFusionIndexCanBePartOfNewFusion();
    void requireThatSelectorsCanBeRebased();
    void requireThatFusionCanBeStopped();

public:
    Test();
    ~Test();
    int Main() override;
};

Test::Test()
    : _fusion_runner(),
      _selector(),
      _fusion_spec(),
      _fileHeaderContext(),
      _service(1),
      _ops(_fileHeaderContext,TuneFileIndexManager(), 0, _service.write())
{ }
Test::~Test() = default;

int
Test::Main()
{
    TEST_INIT("fusionrunner_test");

    if (_argc > 0) {
        DummyFileHeaderContext::setCreator(_argv[0]);
    }
    TEST_CALL(requireThatNoDiskIndexesGiveId0());
    TEST_CALL(requireThatOneDiskIndexCausesCopy());
    TEST_CALL(requireThatTwoDiskIndexesCauseFusion());
    TEST_CALL(requireThatFusionCanRunOnMultipleDiskIndexes());
    TEST_CALL(requireThatOldFusionIndexCanBePartOfNewFusion());
    TEST_CALL(requireThatSelectorsCanBeRebased());
    TEST_CALL(requireThatFusionCanBeStopped());

    TEST_DONE();
}

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

void Test::setUp() {
    std::filesystem::remove_all(std::filesystem::path(base_dir));
    _fusion_runner.reset(new FusionRunner(base_dir, getSchema(),
                                 TuneFileAttributes(),
                                 _fileHeaderContext));
    const string selector_base = base_dir + "/index.flush.0/selector";
    _selector.reset(new FixedSourceSelector(0, selector_base));
    _fusion_spec = FusionSpec();
}

void Test::tearDown() {
    std::filesystem::remove_all(std::filesystem::path(base_dir));
    _selector.reset(0);
}

Document::UP buildDocument(DocBuilder & doc_builder, int id, const string &word) {
    vespalib::asciistream ost;
    ost << "id:ns:searchdocument::" << id;
    auto doc = doc_builder.make_document(ost.str());
    doc->setValue(field_name, StringFieldBuilder(doc_builder).word(word).build());
    return doc;
}

void addDocument(DocBuilder & doc_builder, MemoryIndex &index, ISourceSelector &selector,
                 uint8_t index_id, uint32_t docid, const string &word) {
    Document::UP doc = buildDocument(doc_builder, docid, word);
    index.insertDocument(docid, *doc, {});
    vespalib::Gate gate;
    index.commit(std::make_shared<vespalib::GateCallback>(gate));
    selector.setSource(docid, index_id);
    gate.await();
}

void Test::createIndex(const string &dir, uint32_t id, bool fusion) {
    std::filesystem::create_directory(std::filesystem::path(dir));
    vespalib::asciistream ost;
    if (fusion) {
        ost << dir << "/index.fusion." << id;
        _fusion_spec.last_fusion_id = id;
    } else {
        ost << dir << "/index.flush." << id;
        _fusion_spec.flush_ids.push_back(id);
    }
    const string index_dir = ost.str();
    _selector->setDefaultSource(id - _selector->getBaseId());

    DocBuilder doc_builder(add_fields);
    auto schema = SchemaBuilder(doc_builder).add_all_indexes().build();
    MemoryIndex memory_index(schema, MockFieldLengthInspector(),
                             _service.write().indexFieldInverter(),
                             _service.write().indexFieldWriter());
    addDocument(doc_builder, memory_index, *_selector, id, id + 0, term);
    addDocument(doc_builder, memory_index, *_selector, id, id + 1, "bar");
    addDocument(doc_builder, memory_index, *_selector, id, id + 2, "baz");
    addDocument(doc_builder, memory_index, *_selector, id, id + 3, "qux");

    const uint32_t docIdLimit = std::min(memory_index.getDocIdLimit(), _selector->getDocIdLimit());
    IndexBuilder index_builder(schema, index_dir, docIdLimit);
    TuneFileIndexing tuneFileIndexing;
    TuneFileAttributes tuneFileAttributes;
    index_builder.open(memory_index.getNumWords(), MockFieldLengthInspector(), tuneFileIndexing, _fileHeaderContext);
    memory_index.dump(index_builder);
    index_builder.close();

    _selector->extractSaveInfo(index_dir + "/selector")->save(tuneFileAttributes, _fileHeaderContext);
}

set<uint32_t> readFusionIds(const string &dir) {
    set<uint32_t> ids;
    FastOS_DirectoryScan dir_scan(dir.c_str());
    while (dir_scan.ReadNext()) {
        if (!dir_scan.IsDirectory()) {
            continue;
        }
        vespalib::string name = dir_scan.GetName();
        const vespalib::string prefix("index.fusion.");
        vespalib::string::size_type pos = name.find(prefix);
        if (pos != 0) {
            continue;
        }
        vespalib::string idString = name.substr(prefix.size());
        vespalib::asciistream ist(idString);
        uint32_t id;
        ist >> id;
        ids.insert(id);
    }
    return ids;
}

vespalib::string getFusionIndexName(uint32_t fusion_id) {
   vespalib::asciistream ost;
   ost << base_dir << "/index.fusion." << fusion_id;
   return ost.str();
}

void Test::checkResults(uint32_t fusion_id, const uint32_t *ids, size_t size) {
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
    blueprint->fetchPostings(search::queryeval::ExecuteInfo::TRUE);
    SearchIterator::UP search = blueprint->createSearch(*match_data, true);
    search->initFullRange();
    for (size_t i = 0; i < size; ++i) {
        EXPECT_TRUE(search->seek(ids[i]));
    }
}

void Test::requireThatNoDiskIndexesGiveId0() {
    uint32_t fusion_id = _fusion_runner->fuse(_fusion_spec, 0u, _ops, std::make_shared<search::FlushToken>());
    EXPECT_EQUAL(0u, fusion_id);
}

void Test::requireThatOneDiskIndexCausesCopy() {
    createIndex(base_dir, disk_id[0]);
    uint32_t fusion_id = _fusion_runner->fuse(_fusion_spec, 0u, _ops, std::make_shared<search::FlushToken>());
    EXPECT_EQUAL(disk_id[0], fusion_id);
    set<uint32_t> fusion_ids = readFusionIds(base_dir);
    ASSERT_TRUE(!fusion_ids.empty());
    EXPECT_EQUAL(1u, fusion_ids.size());
    EXPECT_EQUAL(fusion_id, *fusion_ids.begin());

    checkResults(fusion_id, disk_id, 1);
}

void Test::requireThatTwoDiskIndexesCauseFusion() {
    createIndex(base_dir, disk_id[0]);
    createIndex(base_dir, disk_id[1]);
    uint32_t fusion_id = _fusion_runner->fuse(_fusion_spec, 0u, _ops, std::make_shared<search::FlushToken>());
    EXPECT_EQUAL(disk_id[1], fusion_id);
    set<uint32_t> fusion_ids = readFusionIds(base_dir);
    ASSERT_TRUE(!fusion_ids.empty());
    EXPECT_EQUAL(1u, fusion_ids.size());
    EXPECT_EQUAL(fusion_id, *fusion_ids.begin());

    checkResults(fusion_id, disk_id, 2);
}

void Test::requireThatFusionCanRunOnMultipleDiskIndexes() {
    createIndex(base_dir, disk_id[0]);
    createIndex(base_dir, disk_id[1]);
    createIndex(base_dir, disk_id[2]);
    createIndex(base_dir, disk_id[3]);
    uint32_t fusion_id = _fusion_runner->fuse(_fusion_spec, 0u, _ops, std::make_shared<search::FlushToken>());
    EXPECT_EQUAL(disk_id[3], fusion_id);
    set<uint32_t> fusion_ids = readFusionIds(base_dir);
    ASSERT_TRUE(!fusion_ids.empty());
    EXPECT_EQUAL(1u, fusion_ids.size());
    EXPECT_EQUAL(fusion_id, *fusion_ids.begin());

    checkResults(fusion_id, disk_id, 4);
}

void Test::requireThatOldFusionIndexCanBePartOfNewFusion() {
    createIndex(base_dir, disk_id[0], true);
    createIndex(base_dir, disk_id[1]);
    uint32_t fusion_id = _fusion_runner->fuse(_fusion_spec, 0u, _ops, std::make_shared<search::FlushToken>());
    EXPECT_EQUAL(disk_id[1], fusion_id);
    set<uint32_t> fusion_ids = readFusionIds(base_dir);
    ASSERT_TRUE(!fusion_ids.empty());
    EXPECT_EQUAL(2u, fusion_ids.size());
    EXPECT_EQUAL(disk_id[0], *fusion_ids.begin());
    EXPECT_EQUAL(fusion_id, *(++fusion_ids.begin()));

    checkResults(fusion_id, disk_id, 2);
}

void Test::requireThatSelectorsCanBeRebased() {
    createIndex(base_dir, disk_id[0]);
    createIndex(base_dir, disk_id[1]);
    uint32_t fusion_id = _fusion_runner->fuse(_fusion_spec, 0u, _ops, std::make_shared<search::FlushToken>());

    _fusion_spec.flush_ids.clear();
    _fusion_spec.last_fusion_id = fusion_id;
    createIndex(base_dir, disk_id[2]);
    fusion_id = _fusion_runner->fuse(_fusion_spec, 0u, _ops, std::make_shared<search::FlushToken>());

    checkResults(fusion_id, disk_id, 3);
}

void
Test::requireThatFusionCanBeStopped()
{
    createIndex(base_dir, disk_id[0]);
    createIndex(base_dir, disk_id[1]);
    auto flush_token = std::make_shared<search::FlushToken>();
    flush_token->request_stop();
    uint32_t fusion_id = _fusion_runner->fuse(_fusion_spec, 0u, _ops, flush_token);
    EXPECT_EQUAL(0u, fusion_id);
}

}  // namespace

TEST_APPHOOK(Test);
