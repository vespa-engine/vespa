// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/index/indexmanager.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/searchcore/proton/test/transport_helper.h>
#include <vespa/searchcorespi/index/index_manager_stats.h>
#include <vespa/searchcorespi/index/indexcollection.h>
#include <vespa/searchcorespi/index/indexflushtarget.h>
#include <vespa/searchcorespi/index/indexfusiontarget.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>
#include <vespa/searchlib/common/flush_token.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/test/doc_builder.h>
#include <vespa/searchlib/test/schema_builder.h>
#include <vespa/searchlib/test/string_field_builder.h>
#include <vespa/searchlib/memoryindex/compact_words_store.h>
#include <vespa/searchlib/memoryindex/document_inverter.h>
#include <vespa/searchlib/memoryindex/document_inverter_context.h>
#include <vespa/searchlib/memoryindex/field_index_collection.h>
#include <vespa/searchlib/memoryindex/field_inverter.h>
#include <vespa/searchlib/queryeval/isourceselector.h>
#include <vespa/searchlib/test/index/mock_field_length_inspector.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/util/gate.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/fastos/file.h>
#include <filesystem>
#include <ostream>
#include <set>
#include <sstream>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP("indexmanager_test");

using document::Document;
using document::FieldValue;
using document::StringFieldValue;
using proton::index::IndexConfig;
using proton::index::IndexManager;
using vespalib::SequencedTaskExecutor;
using search::SerialNum;
using search::TuneFileAttributes;
using search::TuneFileIndexManager;
using search::TuneFileIndexing;
using vespalib::datastore::EntryRef;
using search::index::DummyFileHeaderContext;
using search::index::FieldLengthInfo;
using search::index::Schema;
using search::index::test::MockFieldLengthInspector;
using search::memoryindex::CompactWordsStore;
using search::memoryindex::FieldIndexCollection;
using search::queryeval::Source;
using search::test::DocBuilder;
using search::test::SchemaBuilder;
using search::test::StringFieldBuilder;
using std::set;
using std::string;
using vespalib::makeLambdaTask;
using std::chrono::duration_cast;

using namespace proton;
using namespace searchcorespi;
using namespace searchcorespi::index;

namespace {

class IndexManagerDummyReconfigurer : public searchcorespi::IIndexManager::Reconfigurer {

    virtual bool reconfigure(std::unique_ptr<Configure> configure) override {
        bool ret = true;
        if (configure) {
            ret = configure->configure(); // Perform index manager reconfiguration now
        }
        return ret;
    }

};

const string index_dir = "test_data";
const string field_name = "field";
const uint32_t docid = 1;

auto add_fields = [](auto& header) { header.addField(field_name, document::DataType::T_STRING); };

Schema getSchema(std::optional<bool> interleaved_features) {
    DocBuilder db(add_fields);
    return SchemaBuilder(db).add_all_indexes(interleaved_features).build();
}

void removeTestData() {
    std::filesystem::remove_all(std::filesystem::path(index_dir));
}

Document::UP buildDocument(DocBuilder &doc_builder, int id,
                           const string &word) {
    vespalib::asciistream ost;
    ost << "id:ns:searchdocument::" << id;
    auto doc = doc_builder.make_document(ost.str());
    doc->setValue(field_name, StringFieldBuilder(doc_builder).word(word).build());
    return doc;
}

void push_documents_and_wait(search::memoryindex::DocumentInverter &inverter) {
    vespalib::Gate gate;
    inverter.pushDocuments(std::make_shared<vespalib::GateCallback>(gate));
    gate.await();
}

struct IndexManagerTest : public ::testing::Test {
    SerialNum _serial_num;
    IndexManagerDummyReconfigurer _reconfigurer;
    DummyFileHeaderContext _fileHeaderContext;
    TransportAndExecutorService _service;
    std::unique_ptr<IndexManager> _index_manager;
    std::optional<bool> _interleaved_features;
    DocBuilder _builder;

    IndexManagerTest()
        : _serial_num(0),
          _reconfigurer(),
          _fileHeaderContext(),
          _service(1),
          _index_manager(),
          _interleaved_features(),
          _builder(add_fields)
    {
        removeTestData();
        std::filesystem::create_directory(std::filesystem::path(index_dir));
        resetIndexManager();
    }

    ~IndexManagerTest() {
        _service.shutdown();
    }

    template <class FunctionType>
    void runAsMaster(FunctionType &&function) {
        vespalib::Gate gate;
        _service.write().master().execute(makeLambdaTask([&gate,function = std::move(function)]() {
            function();
            gate.countDown();
        }));
        gate.await();
    }
    template <class FunctionType>
    void runAsIndex(FunctionType &&function) {
        vespalib::Gate gate;
        _service.write().index().execute(makeLambdaTask([&gate,function = std::move(function)]() {
            function();
            gate.countDown();
        }));
        gate.await();
    }
    void flushIndexManager();
    void run_fusion();
    Document::UP addDocument(uint32_t docid);
    void resetIndexManager(SerialNum serial_num = 1);
    void removeDocument(uint32_t docId, SerialNum serialNum) {
        vespalib::Gate gate;
        runAsIndex([&]() {
            _index_manager->removeDocument(docId, serialNum);
            _index_manager->commit(serialNum, std::make_shared<vespalib::GateCallback>(gate));
        });
        gate.await();
    }
    void removeDocument(uint32_t docId) {
        SerialNum serialNum = ++_serial_num;
        removeDocument(docId, serialNum);
    }
    void compactLidSpace(uint32_t lidLimit) {
        SerialNum serialNum = ++_serial_num;
        runAsIndex([&]() { _index_manager->compactLidSpace(lidLimit, serialNum); });
    }
    void assertStats(uint32_t expNumDiskIndexes,
                     uint32_t expNumMemoryIndexes,
                     SerialNum expLastDiskIndexSerialNum,
                     SerialNum expLastMemoryIndexSerialNum);

    IIndexCollection::SP get_source_collection() const {
        return _index_manager->getMaintainer().getSourceCollection();
    }
    void set_schema(const Schema& schema, SerialNum serial_num)
    {
        runAsMaster([&]() { _index_manager->setSchema(schema, serial_num); });
    }

    bool has_pending_urgent_flush() const {
        return _index_manager->has_pending_urgent_flush();
    }
    bool has_urgent_memory_index_flush() const;
    bool has_urgent_fusion() const;
    void assert_urgent(const vespalib::string& label, bool pending, bool flush, bool fusion);
};

void
IndexManagerTest::flushIndexManager()
{
    vespalib::Executor::Task::UP task;
    SerialNum serialNum = _index_manager->getCurrentSerialNum();
    auto &maintainer = _index_manager->getMaintainer();
    runAsMaster([&]() { task = maintainer.initFlush(serialNum, nullptr); });
    if (task.get()) {
        task->run();
    }
}

void
IndexManagerTest::run_fusion()
{
    IndexFusionTarget target(_index_manager->getMaintainer());
    std::unique_ptr<vespalib::Executor::Task> task;
    runAsMaster([&]() { task = target.initFlush(0, std::make_shared<search::FlushToken>()); });
    if (task) {
        task->run();
    }
}

bool
IndexManagerTest::has_urgent_memory_index_flush() const
{
    IndexFlushTarget target(_index_manager->getMaintainer());
    return target.needUrgentFlush();
}

bool
IndexManagerTest::has_urgent_fusion() const
{
    IndexFusionTarget target(_index_manager->getMaintainer());
    return target.needUrgentFlush();
}

Document::UP
IndexManagerTest::addDocument(uint32_t id)
{
    Document::UP doc = buildDocument(_builder, id, "foo");
    SerialNum serialNum = ++_serial_num;
    vespalib::Gate gate;
    runAsIndex([&]() { _index_manager->putDocument(id, *doc, serialNum, {});
                          _index_manager->commit(serialNum,
                                                 std::make_shared<vespalib::GateCallback>(gate)); });
    gate.await();
    return doc;
}

void
IndexManagerTest::resetIndexManager(SerialNum serial_num)
{
    _index_manager.reset();
    _index_manager = std::make_unique<IndexManager>(index_dir, IndexConfig(), getSchema(_interleaved_features), serial_num,
                             _reconfigurer, _service.write(), _service.shared(),
                             TuneFileIndexManager(), TuneFileAttributes(), _fileHeaderContext);
    _serial_num = std::max(serial_num, _index_manager->getFlushedSerialNum());
}

void
IndexManagerTest::assertStats(uint32_t expNumDiskIndexes, uint32_t expNumMemoryIndexes,
                              SerialNum expLastDiskIndexSerialNum, SerialNum expLastMemoryIndexSerialNum)
{
    searchcorespi::IndexManagerStats stats(*_index_manager);
    SerialNum lastDiskIndexSerialNum = 0;
    SerialNum lastMemoryIndexSerialNum = 0;
    const std::vector<searchcorespi::index::DiskIndexStats> & diskIndexes(stats.getDiskIndexes());
    const std::vector<searchcorespi::index::MemoryIndexStats> & memoryIndexes(stats.getMemoryIndexes());
    if (!diskIndexes.empty()) {
        lastDiskIndexSerialNum = diskIndexes.back().getSerialNum();
    }
    if (!memoryIndexes.empty()) {
        lastMemoryIndexSerialNum = memoryIndexes.back().getSerialNum();
    }
    EXPECT_EQ(expNumDiskIndexes, diskIndexes.size());
    EXPECT_EQ(expNumMemoryIndexes, memoryIndexes.size());
    EXPECT_EQ(expLastDiskIndexSerialNum, lastDiskIndexSerialNum);
    EXPECT_EQ(expLastMemoryIndexSerialNum, lastMemoryIndexSerialNum);
}

void
IndexManagerTest::assert_urgent(const vespalib::string& label, bool pending, bool flush, bool fusion)
{
    SCOPED_TRACE(label);
    EXPECT_EQ(pending, has_pending_urgent_flush());
    EXPECT_EQ(flush, has_urgent_memory_index_flush());
    EXPECT_EQ(fusion, has_urgent_fusion());
}

TEST_F(IndexManagerTest, require_that_empty_memory_index_is_not_flushed)
{
    auto sources = get_source_collection();
    EXPECT_EQ(1u, sources->getSourceCount());

    flushIndexManager();

    sources = get_source_collection();
    EXPECT_EQ(1u, sources->getSourceCount());
}

TEST_F(IndexManagerTest, require_that_empty_memory_index_is_flushed_if_source_selector_changed)
{
    auto sources = get_source_collection();
    EXPECT_EQ(1u, sources->getSourceCount());

    removeDocument(docid, 42);
    flushIndexManager();

    sources = get_source_collection();
    EXPECT_EQ(2u, sources->getSourceCount());
}

set<uint32_t>
readDiskIds(const string &dir, const string &type)
{
    set<uint32_t> ids;
    const string flush_prefix("index." + type + ".");
    std::filesystem::directory_iterator dir_scan(dir);
    for (auto& entry : dir_scan) {
        if (entry.is_directory() && entry.path().filename().string().find(flush_prefix) == 0) {
            auto idString = entry.path().filename().string().substr(flush_prefix.size());
            vespalib::asciistream ist(idString);
            uint32_t id;
            ist >> id;
            ids.insert(id);
        }
    }
    return ids;
}

TEST_F(IndexManagerTest, require_that_memory_index_is_flushed)
{
    using seconds = std::chrono::seconds;
    FastOS_StatInfo stat;
    {
        addDocument(docid);

        auto sources = get_source_collection();
        EXPECT_EQ(1u, sources->getSourceCount());
        EXPECT_EQ(1u, sources->getSourceId(0));

        IndexFlushTarget target(_index_manager->getMaintainer());
        EXPECT_EQ(vespalib::system_time(), target.getLastFlushTime());
        vespalib::Executor::Task::UP flushTask;
        runAsMaster([&]() { flushTask = target.initFlush(2, std::make_shared<search::FlushToken>()); });
        flushTask->run();
        EXPECT_TRUE(FastOS_File::Stat("test_data/index.flush.1", &stat));
        EXPECT_EQ(stat._modifiedTime, target.getLastFlushTime());
        EXPECT_EQ(2u, target.getFlushedSerialNum());

        sources = get_source_collection();
        EXPECT_EQ(2u, sources->getSourceCount());
        EXPECT_EQ(1u, sources->getSourceId(0));
        EXPECT_EQ(2u, sources->getSourceId(1));

        set<uint32_t> disk_ids = readDiskIds(index_dir, "flush");
        ASSERT_TRUE(disk_ids.size() == 1);
        EXPECT_EQ(1u, *disk_ids.begin());

        FlushStats stats = target.getLastFlushStats();
        EXPECT_EQ("test_data/index.flush.1", stats.getPath());
        EXPECT_EQ(7u, stats.getPathElementsToLog());
    }
    { // verify last flush time when loading disk index
        resetIndexManager();
        IndexFlushTarget target(_index_manager->getMaintainer());
        EXPECT_EQ(stat._modifiedTime, target.getLastFlushTime());
        EXPECT_EQ(2u, target.getFlushedSerialNum());

        // updated serial number & flush time when nothing to flush
        std::this_thread::sleep_for(2s);
        std::chrono::seconds now = duration_cast<seconds>(vespalib::system_clock::now().time_since_epoch());
        vespalib::Executor::Task::UP task;
        runAsMaster([&]() { task = target.initFlush(3, std::make_shared<search::FlushToken>()); });
        EXPECT_FALSE(task);
        EXPECT_EQ(3u, target.getFlushedSerialNum());
        EXPECT_LT(stat._modifiedTime, target.getLastFlushTime());
        EXPECT_NEAR(now.count(), duration_cast<seconds>(target.getLastFlushTime().time_since_epoch()).count(), 2);
    }
}

TEST_F(IndexManagerTest, require_that_large_memory_footprint_triggers_urgent_flush) {
    using FlushStats = IndexMaintainer::FlushStats;
    // IndexMaintainer::FlushStats small_15G(15_Gi, 0, 1, 1);
    EXPECT_FALSE(IndexFlushTarget(_index_manager->getMaintainer()).needUrgentFlush());
    EXPECT_FALSE(IndexFlushTarget(_index_manager->getMaintainer(), FlushStats(15_Gi)).needUrgentFlush());
    EXPECT_TRUE(IndexFlushTarget(_index_manager->getMaintainer(), FlushStats(17_Gi)).needUrgentFlush());
}

TEST_F(IndexManagerTest, require_that_flush_priority_is_high) {
    EXPECT_EQ(IFlushTarget::Priority::HIGH, IndexFlushTarget(_index_manager->getMaintainer()).getPriority());
}

TEST_F(IndexManagerTest, require_that_multiple_flushes_gives_multiple_indexes)
{
    size_t flush_count = 10;
    for (size_t i = 0; i < flush_count; ++i) {
        addDocument(docid);
        flushIndexManager();
    }
    set<uint32_t> disk_ids = readDiskIds(index_dir, "flush");
    EXPECT_EQ(flush_count, disk_ids.size());
    uint32_t i = 1;
    for (auto it = disk_ids.begin(); it != disk_ids.end(); ++it) {
        EXPECT_EQ(i++, *it);
    }
}

TEST_F(IndexManagerTest, require_that_max_flushes_sets_urgent)
{
    size_t flush_count = 20;
    for (size_t i = 0; i < flush_count; ++i) {
        addDocument(docid);
        flushIndexManager();
    }
    IndexFusionTarget target(_index_manager->getMaintainer());
    EXPECT_TRUE(target.needUrgentFlush());
}

uint32_t getSource(const IIndexCollection &sources, uint32_t id) {
    return sources.getSourceSelector().createIterator()->getSource(id);
}

TEST_F(IndexManagerTest, require_that_put_document_updates_selector)
{
    addDocument(docid);
    auto sources = get_source_collection();
    EXPECT_EQ(1u, getSource(*sources, docid));
    flushIndexManager();
    addDocument(docid + 1);
    sources = get_source_collection();
    EXPECT_EQ(1u, getSource(*sources, docid));
    EXPECT_EQ(2u, getSource(*sources, docid + 1));
}

TEST_F(IndexManagerTest, require_that_remove_document_updates_selector)
{
    Document::UP doc = addDocument(docid);
    auto sources = get_source_collection();
    EXPECT_EQ(1u, getSource(*sources, docid));
    flushIndexManager();
    removeDocument(docid, ++_serial_num);
    sources = get_source_collection();
    EXPECT_EQ(2u, getSource(*sources, docid));
}

TEST_F(IndexManagerTest, require_that_source_selector_is_flushed)
{
    addDocument(docid);
    flushIndexManager();
    FastOS_File file((index_dir + "/index.flush.1/selector.dat").c_str());
    ASSERT_TRUE(file.OpenReadOnlyExisting());
}

VESPA_THREAD_STACK_TAG(invert_executor)
VESPA_THREAD_STACK_TAG(push_executor)

TEST_F(IndexManagerTest, require_that_flush_stats_are_calculated)
{
    Schema schema(getSchema(_interleaved_features));
    FieldIndexCollection fic(schema, MockFieldLengthInspector());
    auto invertThreads = SequencedTaskExecutor::create(invert_executor, 2);
    auto pushThreads = SequencedTaskExecutor::create(push_executor, 2);
    search::memoryindex::DocumentInverterContext inverter_context(schema, *invertThreads, *pushThreads, fic);
    search::memoryindex::DocumentInverter inverter(inverter_context);

    uint64_t fixed_index_size = fic.getMemoryUsage().allocatedBytes();
    uint64_t index_size = fic.getMemoryUsage().allocatedBytes() - fixed_index_size;
    /// Must account for both docid 0 being reserved and the extra after.
    uint64_t selector_size = (1) * sizeof(Source);
    EXPECT_EQ(index_size, _index_manager->getMaintainer().getFlushStats().memory_before_bytes -
                          _index_manager->getMaintainer().getFlushStats().memory_after_bytes);
    EXPECT_EQ(0u, _index_manager->getMaintainer().getFlushStats().disk_write_bytes);
    EXPECT_EQ(0u, _index_manager->getMaintainer().getFlushStats().cpu_time_required);

    Document::UP doc = addDocument(docid);
    inverter.invertDocument(docid, *doc, {});
    push_documents_and_wait(inverter);
    index_size = fic.getMemoryUsage().allocatedBytes() - fixed_index_size;

    /// Must account for both docid 0 being reserved and the extra after.
    selector_size = (docid + 1) * sizeof(Source);
    EXPECT_EQ(index_size,
              _index_manager->getMaintainer().getFlushStats().memory_before_bytes -
              _index_manager->getMaintainer().getFlushStats().memory_after_bytes);
    EXPECT_EQ(selector_size + index_size,
              _index_manager->getMaintainer().getFlushStats().disk_write_bytes);
    EXPECT_EQ(selector_size * (3+1) + index_size,
              _index_manager->getMaintainer().getFlushStats().cpu_time_required);

    doc = addDocument(docid + 10);
    inverter.invertDocument(docid + 10, *doc, {});
    auto doc100 = addDocument(docid + 100);
    inverter.invertDocument(docid + 100, *doc100, {});
    push_documents_and_wait(inverter);
    index_size = fic.getMemoryUsage().allocatedBytes() - fixed_index_size;
    /// Must account for both docid 0 being reserved and the extra after.
    selector_size = (docid + 100 + 1) * sizeof(Source);
    EXPECT_EQ(index_size,
              _index_manager->getMaintainer().getFlushStats().memory_before_bytes -
              _index_manager->getMaintainer().getFlushStats().memory_after_bytes);
    EXPECT_EQ(selector_size + index_size,
              _index_manager->getMaintainer().getFlushStats().disk_write_bytes);
    EXPECT_EQ(selector_size * (3+1) + index_size,
              _index_manager->getMaintainer().getFlushStats().cpu_time_required);
}

TEST_F(IndexManagerTest, require_that_fusion_stats_are_calculated)
{
    addDocument(docid);
    EXPECT_EQ(0u, _index_manager->getMaintainer().getFusionStats().diskUsage);
    flushIndexManager();
    ASSERT_TRUE(_index_manager->getMaintainer().getFusionStats().diskUsage > 0);
}

TEST_F(IndexManagerTest, require_that_put_document_updates_serial_num)
{
    _serial_num = 0;
    EXPECT_EQ(0u, _index_manager->getCurrentSerialNum());
    addDocument(docid);
    EXPECT_EQ(1u, _index_manager->getCurrentSerialNum());
}

TEST_F(IndexManagerTest, require_that_remove_document_updates_serial_num)
{
    _serial_num = 0;
    Document::UP doc = addDocument(docid);
    EXPECT_EQ(1u, _index_manager->getCurrentSerialNum());
    removeDocument(docid, ++_serial_num);
    EXPECT_EQ(2u, _index_manager->getCurrentSerialNum());
}

TEST_F(IndexManagerTest, require_that_flush_updates_serial_num)
{
    _serial_num = 0;
    addDocument(docid);
    EXPECT_EQ(1u, _index_manager->getCurrentSerialNum());
    EXPECT_EQ(0u, _index_manager->getFlushedSerialNum());
    flushIndexManager();
    EXPECT_EQ(1u, _index_manager->getCurrentSerialNum());
    EXPECT_EQ(1u, _index_manager->getFlushedSerialNum());
}

TEST_F(IndexManagerTest, require_that_fusion_updates_indexes)
{
    for (size_t i = 0; i < 10; ++i) {
        addDocument(docid + i);
        flushIndexManager();
    }
    uint32_t ids[] = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
    auto sources = get_source_collection();
    EXPECT_EQ(10u + 1, sources->getSourceCount());  // disk + mem
    EXPECT_EQ(ids[2], getSource(*sources, docid + 2));
    EXPECT_EQ(ids[6], getSource(*sources, docid + 6));

    FusionSpec fusion_spec;
    fusion_spec.flush_ids.assign(ids, ids + 4);
    _index_manager->getMaintainer().runFusion(fusion_spec, std::make_shared<search::FlushToken>());

    set<uint32_t> fusion_ids = readDiskIds(index_dir, "fusion");
    EXPECT_EQ(1u, fusion_ids.size());
    EXPECT_EQ(ids[3], *fusion_ids.begin());

    sources = get_source_collection();
    EXPECT_EQ(10u + 1 - 4 + 1, sources->getSourceCount());
    EXPECT_EQ(0u, getSource(*sources, docid + 2));
    EXPECT_EQ(3u, getSource(*sources, docid + 6));
}

TEST_F(IndexManagerTest, require_that_flush_triggers_fusion)
{
    const uint32_t fusion_trigger = 5;
    resetIndexManager();

    for (size_t i = 1; i <= fusion_trigger; ++i) {
        addDocument(docid);
        flushIndexManager();
    }
    IFlushTarget::SP target(new IndexFusionTarget(_index_manager->getMaintainer()));
    target->initFlush(0, std::make_shared<search::FlushToken>())->run();
    addDocument(docid);
    flushIndexManager();
    set<uint32_t> fusion_ids = readDiskIds(index_dir, "fusion");
    EXPECT_EQ(1u, fusion_ids.size());
    EXPECT_EQ(5u, *fusion_ids.begin());
    set<uint32_t> flush_ids = readDiskIds(index_dir, "flush");
    EXPECT_EQ(1u, flush_ids.size());
    EXPECT_EQ(6u, *flush_ids.begin());
}

TEST_F(IndexManagerTest, require_that_fusion_target_is_setUp)
{
    addDocument(docid);
    flushIndexManager();
    addDocument(docid);
    flushIndexManager();
    IFlushTarget::List lst(_index_manager->getFlushTargets());
    EXPECT_EQ(2u, lst.size());
    IFlushTarget::SP target(lst.at(1));
    EXPECT_EQ("memoryindex.fusion", target->getName());
    EXPECT_FALSE(target->needUrgentFlush());
    addDocument(docid);
    flushIndexManager();
    lst = _index_manager->getFlushTargets();
    EXPECT_EQ(2u, lst.size());
    target = lst.at(1);
    EXPECT_EQ("memoryindex.fusion", target->getName());
    EXPECT_TRUE(target->needUrgentFlush());
}

TEST_F(IndexManagerTest, require_that_fusion_cleans_up_old_indexes)
{
    addDocument(docid);
    flushIndexManager();
    // hold reference to index.flush.1
    auto fsc = get_source_collection();

    addDocument(docid + 1);
    flushIndexManager();

    set<uint32_t> flush_ids = readDiskIds(index_dir, "flush");
    EXPECT_EQ(2u, flush_ids.size());

    FusionSpec fusion_spec;
    fusion_spec.flush_ids.push_back(1);
    fusion_spec.flush_ids.push_back(2);
    _index_manager->getMaintainer().runFusion(fusion_spec, std::make_shared<search::FlushToken>());

    flush_ids = readDiskIds(index_dir, "flush");
    EXPECT_EQ(1u, flush_ids.size());
    EXPECT_EQ(1u, *flush_ids.begin());

    fsc.reset();
    _index_manager->getMaintainer().removeOldDiskIndexes();
    flush_ids = readDiskIds(index_dir, "flush");
    EXPECT_EQ(0u, flush_ids.size());
}

bool contains(const IIndexCollection &fsc, uint32_t id) {
    set<uint32_t> ids;
    for (size_t i = 0; i < fsc.getSourceCount(); ++i) {
        ids.insert(fsc.getSourceId(i));
    }
    return ids.find(id) != ids.end();
}

bool indexExists(const string &type, uint32_t id) {
    set<uint32_t> disk_ids = readDiskIds(index_dir, type);
    return disk_ids.find(id) != disk_ids.end();
}

TEST_F(IndexManagerTest, require_that_disk_indexes_are_loaded_on_startup)
{
    addDocument(docid);
    flushIndexManager();
    _index_manager.reset(0);

    ASSERT_TRUE(indexExists("flush", 1));
    resetIndexManager();

    auto fsc = get_source_collection();
    EXPECT_EQ(2u, fsc->getSourceCount());
    EXPECT_TRUE(contains(*fsc, 1u));
    EXPECT_TRUE(contains(*fsc, 2u));
    EXPECT_EQ(1u, getSource(*fsc, docid));
    fsc.reset();


    addDocument(docid + 1);
    flushIndexManager();
    ASSERT_TRUE(indexExists("flush", 2));
    FusionSpec fusion_spec;
    fusion_spec.flush_ids.push_back(1);
    fusion_spec.flush_ids.push_back(2);
    _index_manager->getMaintainer().runFusion(fusion_spec, std::make_shared<search::FlushToken>());
    _index_manager.reset(0);

    ASSERT_TRUE(!indexExists("flush", 1));
    ASSERT_TRUE(!indexExists("flush", 2));
    ASSERT_TRUE(indexExists("fusion", 2));
    resetIndexManager();

    fsc = get_source_collection();
    EXPECT_EQ(2u, fsc->getSourceCount());
    EXPECT_TRUE(contains(*fsc, 0u));
    EXPECT_TRUE(contains(*fsc, 1u));
    EXPECT_EQ(0u, getSource(*fsc, docid));
    EXPECT_EQ(0u, getSource(*fsc, docid + 1));
    /// Must account for both docid 0 being reserved and the extra after.
    EXPECT_EQ(docid + 2, fsc->getSourceSelector().getDocIdLimit());
    fsc.reset();


    addDocument(docid + 2);
    flushIndexManager();
    _index_manager.reset(0);

    ASSERT_TRUE(indexExists("fusion", 2));
    ASSERT_TRUE(indexExists("flush", 3));
    resetIndexManager();

    fsc = get_source_collection();
    EXPECT_EQ(3u, fsc->getSourceCount());
    EXPECT_TRUE(contains(*fsc, 0u));
    EXPECT_TRUE(contains(*fsc, 1u));
    EXPECT_TRUE(contains(*fsc, 2u));
    EXPECT_EQ(0u, getSource(*fsc, docid));
    EXPECT_EQ(0u, getSource(*fsc, docid + 1));
    EXPECT_EQ(1u, getSource(*fsc, docid + 2));
    fsc.reset();
}

TEST_F(IndexManagerTest, require_that_existing_indexes_are_to_be_fusioned_on_startup)
{
    addDocument(docid);
    flushIndexManager();
    addDocument(docid + 1);
    flushIndexManager();
    resetIndexManager();

    IFlushTarget::SP target(new IndexFusionTarget(_index_manager->getMaintainer()));
    target->initFlush(0, std::make_shared<search::FlushToken>())->run();
    addDocument(docid);
    flushIndexManager();

    set<uint32_t> fusion_ids = readDiskIds(index_dir, "fusion");
    EXPECT_EQ(1u, fusion_ids.size());
    EXPECT_EQ(2u, *fusion_ids.begin());
}

TEST_F(IndexManagerTest, require_that_serial_number_is_written_on_flush)
{
    addDocument(docid);
    flushIndexManager();
    FastOS_File file((index_dir + "/index.flush.1/serial.dat").c_str());
    EXPECT_TRUE(file.OpenReadOnly());
}

TEST_F(IndexManagerTest, require_that_serial_number_is_copied_on_fusion)
{
    addDocument(docid);
    flushIndexManager();
    addDocument(docid);
    flushIndexManager();
    FusionSpec fusion_spec;
    fusion_spec.flush_ids.push_back(1);
    fusion_spec.flush_ids.push_back(2);
    _index_manager->getMaintainer().runFusion(fusion_spec, std::make_shared<search::FlushToken>());
    FastOS_File file((index_dir + "/index.fusion.2/serial.dat").c_str());
    EXPECT_TRUE(file.OpenReadOnly());
}

TEST_F(IndexManagerTest, require_that_serial_number_is_read_on_load)
{
    addDocument(docid);
    flushIndexManager();
    EXPECT_EQ(_serial_num, _index_manager->getFlushedSerialNum());
    resetIndexManager();
    EXPECT_EQ(_serial_num, _index_manager->getFlushedSerialNum());

    addDocument(docid);
    flushIndexManager();
    addDocument(docid);
    flushIndexManager();
    search::SerialNum serial = _serial_num;
    addDocument(docid);
    resetIndexManager();
    EXPECT_EQ(serial, _index_manager->getFlushedSerialNum());
}

void crippleFusion(uint32_t fusionId) {
    vespalib::asciistream ost;
    ost << index_dir << "/index.flush." << fusionId << "/serial.dat";
    std::filesystem::remove(std::filesystem::path(ost.str()));
}

TEST_F(IndexManagerTest, require_that_failed_fusion_is_retried)
{
    resetIndexManager();

    addDocument(docid);
    flushIndexManager();
    addDocument(docid);
    flushIndexManager();

    crippleFusion(2);

    IndexFusionTarget target(_index_manager->getMaintainer());
    vespalib::Executor::Task::UP fusionTask = target.initFlush(1, std::make_shared<search::FlushToken>());
    fusionTask->run();

    FusionSpec spec = _index_manager->getMaintainer().getFusionSpec();
    set<uint32_t> fusion_ids = readDiskIds(index_dir, "fusion");
    EXPECT_TRUE(fusion_ids.empty());
    EXPECT_EQ(0u, spec.last_fusion_id);
    EXPECT_EQ(2u, spec.flush_ids.size());
    EXPECT_EQ(1u, spec.flush_ids[0]);
    EXPECT_EQ(2u, spec.flush_ids[1]);
}

namespace {

void expectSchemaIndexFields(uint32_t expIndexFields) {
    Schema s;
    s.loadFromFile("test_data/index.flush.1/schema.txt");
    EXPECT_EQ(expIndexFields, s.getNumIndexFields());
}

}

TEST_F(IndexManagerTest, require_that_setSchema_updates_schema_on_disk_wiping_removed_fields)
{
    Schema empty_schema;
    addDocument(docid);
    flushIndexManager();
    expectSchemaIndexFields(1);
    runAsMaster([&]() { _index_manager->setSchema(empty_schema, ++_serial_num); });
    expectSchemaIndexFields(0);
}

TEST_F(IndexManagerTest, require_that_indexes_manager_stats_can_be_generated)
{
    assertStats(0, 1, 0, 0);
    addDocument(1);
    assertStats(0, 1, 0, 2);
    flushIndexManager();
    assertStats(1, 1, 2, 2);
    addDocument(2);
    assertStats(1, 1, 2, 3);
}

TEST_F(IndexManagerTest, require_that_compact_lid_space_works)
{
    Schema empty_schema;
    addDocument(1);
    addDocument(2);
    removeDocument(2);
    auto fsc = get_source_collection();
    EXPECT_EQ(3u, fsc->getSourceSelector().getDocIdLimit());
    compactLidSpace(2);
    EXPECT_EQ(2u, fsc->getSourceSelector().getDocIdLimit());
}

template <typename IndexType>
IndexType*
as_index_type(const IIndexCollection& col, uint32_t source_id)
{
    auto& searchable = col.getSearchable(source_id);
    auto* result = dynamic_cast<IndexType *>(&searchable);
    assert(result != nullptr);
    return result;
}

IMemoryIndex*
as_memory_index(const IIndexCollection& col, uint32_t source_id)
{
    return as_index_type<IMemoryIndex>(col, source_id);
}

IDiskIndex*
as_disk_index(const IIndexCollection& col, uint32_t source_id)
{
    return as_index_type<IDiskIndex>(col, source_id);
}

void
expect_field_length_info(double exp_average, uint32_t exp_samples, const IndexSearchable& searchable)
{
    auto info = searchable.get_field_length_info(field_name);
    EXPECT_DOUBLE_EQ(exp_average, info.get_average_field_length());
    EXPECT_EQ(exp_samples, info.get_num_samples());
}

TEST_F(IndexManagerTest, field_length_info_is_propagated_to_disk_index_and_next_memory_index_during_flush)
{
    addDocument(1);
    addDocument(2);

    auto sources = get_source_collection();
    ASSERT_EQ(1, sources->getSourceCount());
    auto *first_index = as_memory_index(*sources, 0);
    expect_field_length_info(1, 2, *first_index);

    flushIndexManager();

    sources = get_source_collection();
    ASSERT_EQ(2, sources->getSourceCount());
    expect_field_length_info(1, 2, *as_disk_index(*sources, 0));
    auto *second_index = as_memory_index(*sources, 1);
    EXPECT_NE(first_index, second_index);
    expect_field_length_info(1, 2, *second_index);
}

TEST_F(IndexManagerTest, field_length_info_is_loaded_from_disk_index_during_startup)
{
    addDocument(1);
    addDocument(2);
    flushIndexManager();
    resetIndexManager();

    auto sources = get_source_collection();
    ASSERT_EQ(2, sources->getSourceCount());
    expect_field_length_info(1, 2, *as_disk_index(*sources, 0));
    expect_field_length_info(1, 2, *as_memory_index(*sources, 1));
}

TEST_F(IndexManagerTest, fusion_can_be_stopped)
{
    resetIndexManager();

    addDocument(docid);
    flushIndexManager();
    addDocument(docid);
    flushIndexManager();

    IndexFusionTarget target(_index_manager->getMaintainer());
    auto flush_token = std::make_shared<search::FlushToken>();
    flush_token->request_stop();
    vespalib::Executor::Task::UP fusionTask = target.initFlush(1, flush_token);
    fusionTask->run();

    FusionSpec spec = _index_manager->getMaintainer().getFusionSpec();
    set<uint32_t> fusion_ids = readDiskIds(index_dir, "fusion");
    EXPECT_TRUE(fusion_ids.empty());
    EXPECT_EQ(0u, spec.last_fusion_id);
    EXPECT_EQ(2u, spec.flush_ids.size());
    EXPECT_EQ(1u, spec.flush_ids[0]);
    EXPECT_EQ(2u, spec.flush_ids[1]);
}

struct EnableInterleavedFeaturesParam
{
    enum class Restart {
        NONE,
        RESTART1,
        RESTART2
    };
    vespalib::string name = "no_restart";
    Restart restart = Restart::NONE;
    bool doc = false;          // Feed doc after enabling interleaved fatures
    bool pruned_config = false; // Original config has been pruned

    EnableInterleavedFeaturesParam no_doc_restart1() && {
        name = "restart1";
        restart = Restart::RESTART1;
        return *this;
    }
    EnableInterleavedFeaturesParam doc_restart1() && {
        name = "doc_restart1";
        restart = Restart::RESTART1;
        doc = true;
        return *this;
    }
    EnableInterleavedFeaturesParam doc_restart2() && {
        name = "doc_restart2";
        restart = Restart::RESTART2;
        doc = true;
        return *this;
    }
    EnableInterleavedFeaturesParam doc_restart2_pruned_config() && {
        name = "doc_restart2_pruned_config";
        restart = Restart::RESTART2;
        doc = true;
        pruned_config = true;
        return *this;
    }
};

std::ostream& operator<<(std::ostream& os, const EnableInterleavedFeaturesParam& param)
{
    os << param.name;
    return os;
}

std::string
param_as_string(const testing::TestParamInfo<std::tuple<bool, bool, EnableInterleavedFeaturesParam>>& info)
{
    std::ostringstream os;
    auto& param = info.param;
    os << (std::get<0>(param) ? "disk_" : "");
    os << (std::get<1>(param) ? "m1_" : "m0_");
    os << std::get<2>(param);
    return os.str();
}

class IndexManagerEnableInterleavedFeaturesTest : public IndexManagerTest,
                                                  public testing::WithParamInterface<std::tuple<bool, bool, EnableInterleavedFeaturesParam>>
{
protected:
    void enable_interleaved_features(const vespalib::string& label, bool old_config_docs, bool flushed_interleaved_features, std::optional<SerialNum> serial_num = std::nullopt);
};

void
IndexManagerEnableInterleavedFeaturesTest::enable_interleaved_features(const vespalib::string& label, bool old_config_docs, bool flushed_interleaved_features, std::optional<SerialNum> serial_num)
{
    if (!serial_num.has_value()) {
        serial_num = ++_serial_num;
    }
    set_schema(getSchema(true), serial_num.value());
    assert_urgent(label, old_config_docs, old_config_docs && !flushed_interleaved_features, old_config_docs && flushed_interleaved_features);
}

TEST_P(IndexManagerEnableInterleavedFeaturesTest, enable_interleaved_features)
{
    using Restart = EnableInterleavedFeaturesParam::Restart;
    const auto& params = GetParam();
    // State before enabling interleaved features
    bool initial_disk_index = std::get<0>(params);
    bool nonempty_memory_index = std::get<1>(params);
    // State for after enabling interleaved features
    const auto& enable_params = std::get<2>(params);
    bool old_config_docs = false;

    _interleaved_features = false;
    SerialNum config_serial_num = 1;
    resetIndexManager(config_serial_num);
    if (initial_disk_index) {
        // Feed doc to memory index without interleaved features and flush
        // memory index to disk
        addDocument(docid);
        old_config_docs = true;
        flushIndexManager();
    }
    if (nonempty_memory_index) {
        // Feed doc to memory index without interleaved features
        addDocument(docid + 1);
        old_config_docs = true;
    }
    assert_urgent("setup", false, false, false);
    enable_interleaved_features("enable interleaved features", old_config_docs, false);
    auto schema_change_serial_num = _serial_num;
    EXPECT_EQ(2 + (initial_disk_index ? 1 : 0) + (nonempty_memory_index ? 1 : 0), schema_change_serial_num);
    if (enable_params.restart == Restart::RESTART1) {
        // Restart after flushing 1st memory index without interleaved features
        resetIndexManager(config_serial_num);
        assert_urgent("after restart1", false, false, false);
        if (nonempty_memory_index) {
            addDocument(docid + 1);
        }
        EXPECT_EQ(schema_change_serial_num, _serial_num + 1);
        enable_interleaved_features("replay enable interleaved features after restart1", old_config_docs, false);
    }
    if (enable_params.doc) {
        // Feed second doc to memory index with interleaved features
        addDocument(docid + 2);
    }
    SerialNum disk2_serial_num = schema_change_serial_num + (enable_params.doc ? 1 : 0);
    EXPECT_EQ(disk2_serial_num, _serial_num);
    flushIndexManager();
    assert_urgent("after 2nd flush", old_config_docs, false, old_config_docs);
    if (enable_params.pruned_config) {
        // Original config has been pruned
        _interleaved_features = true;
        config_serial_num = schema_change_serial_num;
    }
    if (enable_params.restart == Restart::RESTART2) {
        // Restart after flushing 2nd memory index with interleaved fatures
        resetIndexManager(config_serial_num);
        assert_urgent("after restart2", old_config_docs, false, old_config_docs);
        EXPECT_EQ(disk2_serial_num, _serial_num);
        enable_interleaved_features("replay enable interleaved features after restart2", old_config_docs, true, schema_change_serial_num);
    }
    run_fusion();
    assert_urgent("after fusion", false, false, false);
}

auto test_values = testing::Combine(testing::Bool(), testing::Bool(),
                                    testing::Values(EnableInterleavedFeaturesParam(),
                                                    EnableInterleavedFeaturesParam().no_doc_restart1(),
                                                    EnableInterleavedFeaturesParam().doc_restart1(),
                                                    EnableInterleavedFeaturesParam().doc_restart2(),
                                                    EnableInterleavedFeaturesParam().doc_restart2_pruned_config()));

INSTANTIATE_TEST_SUITE_P(MultiIndexManagerEnableInterleavedFeaturesTest, IndexManagerEnableInterleavedFeaturesTest, test_values, param_as_string);

}  // namespace

int
main(int argc, char* argv[])
{
    removeTestData();
    DummyFileHeaderContext::setCreator("indexmanager_test");
    ::testing::InitGoogleTest(&argc, argv);
    int result = RUN_ALL_TESTS();
    removeTestData();
    return result;
}
