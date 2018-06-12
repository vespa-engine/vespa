// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for fusionrunner.

#include <vespa/searchcore/proton/index/indexmanager.h>
#include <vespa/searchcore/proton/server/executorthreadingservice.h>
#include <vespa/searchcorespi/index/fusionrunner.h>
#include <vespa/searchlib/memoryindex/memoryindex.h>
#include <vespa/searchlib/diskindex/diskindex.h>
#include <vespa/searchlib/diskindex/indexbuilder.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/index/docbuilder.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/common/isequencedtaskexecutor.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/fastos/file.h>
#include <set>

using document::Document;
using document::FieldValue;
using proton::ExecutorThreadingService;
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
using search::index::DocBuilder;
using search::index::DummyFileHeaderContext;
using search::index::Schema;
using search::index::schema::DataType;
using search::memoryindex::MemoryIndex;
using search::query::SimpleStringTerm;
using search::queryeval::Blueprint;
using search::queryeval::FakeRequestContext;
using search::queryeval::FieldSpec;
using search::queryeval::FieldSpecList;
using search::queryeval::ISourceSelector;
using search::queryeval::SearchIterator;
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
    ExecutorThreadingService _threadingService;
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

public:
    Test()
        : _fusion_runner(),
          _selector(),
          _fusion_spec(),
          _fileHeaderContext(),
          _threadingService(),
          _ops(_fileHeaderContext,
               TuneFileIndexManager(), 0,
               _threadingService)
    {}
    ~Test() {}
    int Main() override;
};

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

    TEST_DONE();
}

const string base_dir = "fusion_test_data";
const string field_name = "field_name";
const string term = "foo";
const uint32_t disk_id[] = { 1, 2, 21, 42 };

Schema getSchema() {
    Schema schema;
    schema.addIndexField(
            Schema::IndexField(field_name, DataType::STRING));
    return schema;
}

void Test::setUp() {
    FastOS_FileInterface::EmptyAndRemoveDirectory(base_dir.c_str());
    _fusion_runner.reset(new FusionRunner(base_dir, getSchema(),
                                 TuneFileAttributes(),
                                 _fileHeaderContext));
    const string selector_base = base_dir + "/index.flush.0/selector";
    _selector.reset(new FixedSourceSelector(0, selector_base));
    _fusion_spec = FusionSpec();
}

void Test::tearDown() {
    FastOS_FileInterface::EmptyAndRemoveDirectory(base_dir.c_str());
    _selector.reset(0);
}

Document::UP buildDocument(DocBuilder & doc_builder, int id, const string &word) {
    vespalib::asciistream ost;
    ost << "doc::" << id;
    doc_builder.startDocument(ost.str());
    doc_builder.startIndexField(field_name).addStr(word).endField();
    return doc_builder.endDocument();
}

void addDocument(DocBuilder & doc_builder, MemoryIndex &index, ISourceSelector &selector,
                 uint8_t index_id, uint32_t docid, const string &word) {
    Document::UP doc = buildDocument(doc_builder, docid, word);
    index.insertDocument(docid, *doc);
    index.commit(std::shared_ptr<search::IDestructorCallback>());
    selector.setSource(docid, index_id);
}

void Test::createIndex(const string &dir, uint32_t id, bool fusion) {
    FastOS_FileInterface::MakeDirIfNotPresentOrExit(dir.c_str());
    vespalib::asciistream ost;
    if (fusion) {
        ost << dir << "/index.fusion." << id;
        _fusion_spec.last_fusion_id = id;
    } else {
        ost << dir << "/index.flush." << id;
        _fusion_spec.flush_ids.push_back(id);
    }
    const string index_dir = ost.str();

    Schema schema = getSchema();
    DocBuilder doc_builder(schema);
    MemoryIndex memory_index(schema, _threadingService.indexFieldInverter(),
                             _threadingService.indexFieldWriter());
    addDocument(doc_builder, memory_index, *_selector, id, id + 0, term);
    addDocument(doc_builder, memory_index, *_selector, id, id + 1, "bar");
    addDocument(doc_builder, memory_index, *_selector, id, id + 2, "baz");
    addDocument(doc_builder, memory_index, *_selector, id, id + 3, "qux");
    _threadingService.indexFieldWriter().sync();

    const uint32_t docIdLimit =
        std::min(memory_index.getDocIdLimit(), _selector->getDocIdLimit());
    IndexBuilder index_builder(schema);
    index_builder.setPrefix(index_dir);
    TuneFileIndexing tuneFileIndexing;
    TuneFileAttributes tuneFileAttributes;
    index_builder.open(docIdLimit, memory_index.getNumWords(),
                       tuneFileIndexing,
                       _fileHeaderContext);
    memory_index.dump(index_builder);
    index_builder.close();

    _selector->extractSaveInfo(index_dir + "/selector")->
        save(tuneFileAttributes, _fileHeaderContext);
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
    blueprint->fetchPostings(true);
    SearchIterator::UP search = blueprint->createSearch(*match_data, true);
    search->initFullRange();
    for (size_t i = 0; i < size; ++i) {
        EXPECT_TRUE(search->seek(ids[i]));
    }
}

void Test::requireThatNoDiskIndexesGiveId0() {
    uint32_t fusion_id = _fusion_runner->fuse(_fusion_spec, 0u, _ops);
    EXPECT_EQUAL(0u, fusion_id);
}

void Test::requireThatOneDiskIndexCausesCopy() {
    createIndex(base_dir, disk_id[0]);
    uint32_t fusion_id = _fusion_runner->fuse(_fusion_spec, 0u, _ops);
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
    uint32_t fusion_id = _fusion_runner->fuse(_fusion_spec, 0u, _ops);
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
    uint32_t fusion_id = _fusion_runner->fuse(_fusion_spec, 0u, _ops);
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
    uint32_t fusion_id = _fusion_runner->fuse(_fusion_spec, 0u, _ops);
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
    uint32_t fusion_id = _fusion_runner->fuse(_fusion_spec, 0u, _ops);

    _fusion_spec.flush_ids.clear();
    _fusion_spec.last_fusion_id = fusion_id;
    createIndex(base_dir, disk_id[2]);
    fusion_id = _fusion_runner->fuse(_fusion_spec, 0u, _ops);

    checkResults(fusion_id, disk_id, 3);
}

}  // namespace

TEST_APPHOOK(Test);
