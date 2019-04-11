// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for IndexManager.

#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/fieldvalue.h>
#include <vespa/fastos/file.h>
#include <vespa/searchcore/proton/index/indexmanager.h>
#include <vespa/searchcore/proton/server/executorthreadingservice.h>
#include <vespa/searchcorespi/index/index_manager_stats.h>
#include <vespa/searchcorespi/index/indexcollection.h>
#include <vespa/searchcorespi/index/indexflushtarget.h>
#include <vespa/searchcorespi/index/indexfusiontarget.h>
#include <vespa/searchlib/common/sequencedtaskexecutor.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/searchlib/index/docbuilder.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/memoryindex/compact_document_words_store.h>
#include <vespa/searchlib/memoryindex/document_inverter.h>
#include <vespa/searchlib/memoryindex/field_index_collection.h>
#include <vespa/searchlib/memoryindex/field_inverter.h>
#include <vespa/searchlib/queryeval/isourceselector.h>
#include <vespa/searchlib/util/dirtraverse.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <set>

#include <vespa/log/log.h>
LOG_SETUP("indexmanager_test");

using document::Document;
using document::FieldValue;
using search::SequencedTaskExecutor;
using search::SerialNum;
using search::TuneFileAttributes;
using search::TuneFileIndexManager;
using search::TuneFileIndexing;
using search::datastore::EntryRef;
using search::index::DocBuilder;
using search::index::DummyFileHeaderContext;
using search::index::Schema;
using search::index::schema::DataType;
using vespalib::makeLambdaTask;
using search::memoryindex::CompactDocumentWordsStore;
using search::memoryindex::FieldIndexCollection;
using search::queryeval::Source;
using std::set;
using std::string;
using vespalib::BlockingThreadStackExecutor;
using vespalib::ThreadStackExecutor;
using proton::index::IndexManager;
using proton::index::IndexConfig;

using namespace proton;
using namespace searchcorespi;
using namespace searchcorespi::index;

namespace {

class IndexManagerDummyReconfigurer : public searchcorespi::IIndexManager::Reconfigurer
{
    virtual bool
    reconfigure(vespalib::Closure0<bool>::UP closure) override
    {
        bool ret = true;
        if (closure.get() != NULL)
            ret = closure->call(); // Perform index manager reconfiguration now
        return ret;
    }

};

const string index_dir = "test_data";
const string field_name = "field";
const uint32_t docid = 1;

Schema getSchema() {
    Schema schema;
    schema.addIndexField(Schema::IndexField(field_name, DataType::STRING));
    return schema;
}

void removeTestData() {
    FastOS_FileInterface::EmptyAndRemoveDirectory(index_dir.c_str());
}

Document::UP buildDocument(DocBuilder &doc_builder, int id,
                           const string &word) {
    vespalib::asciistream ost;
    ost << "doc::" << id;
    doc_builder.startDocument(ost.str());
    doc_builder.startIndexField(field_name).addStr(word).endField();
    return doc_builder.endDocument();
}

std::shared_ptr<search::IDestructorCallback> emptyDestructorCallback;

struct Fixture {
    SerialNum _serial_num;
    IndexManagerDummyReconfigurer _reconfigurer;
    DummyFileHeaderContext _fileHeaderContext;
    ExecutorThreadingService _writeService;
    std::unique_ptr<IndexManager> _index_manager;
    Schema _schema;
    DocBuilder _builder;

    Fixture()
        : _serial_num(0),
          _reconfigurer(),
          _fileHeaderContext(),
          _writeService(),
          _index_manager(),
          _schema(getSchema()),
          _builder(_schema)
    {
        removeTestData();
        vespalib::mkdir(index_dir, false);
        _writeService.sync();
        resetIndexManager();
    }

    ~Fixture() {
        _writeService.shutdown();
    }

    template <class FunctionType>
    inline void runAsMaster(FunctionType &&function) {
        _writeService.master().execute(makeLambdaTask(std::move(function)));
        _writeService.master().sync();
    }
    template <class FunctionType>
    inline void runAsIndex(FunctionType &&function) {
        _writeService.index().execute(makeLambdaTask(std::move(function)));
        _writeService.index().sync();
    }
    void flushIndexManager();
    Document::UP addDocument(uint32_t docid);
    void resetIndexManager();
    void removeDocument(uint32_t docId, SerialNum serialNum) {
        runAsIndex([&]() { _index_manager->removeDocument(docId, serialNum);
                              _index_manager->commit(serialNum,
                                                     emptyDestructorCallback);
                          });
        _writeService.indexFieldWriter().sync();
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
                     SerialNum expLastiskIndexSerialNum,
                     SerialNum expLastMemoryIndexSerialNum);
};

void Fixture::flushIndexManager() {
    vespalib::Executor::Task::UP task;
    SerialNum serialNum = _index_manager->getCurrentSerialNum();
    auto &maintainer = _index_manager->getMaintainer();
    runAsMaster([&]() { task = maintainer.initFlush(serialNum, NULL); });
    if (task.get()) {
        task->run();
    }
}

Document::UP Fixture::addDocument(uint32_t id) {
    Document::UP doc = buildDocument(_builder, id, "foo");
    SerialNum serialNum = ++_serial_num;
    runAsIndex([&]() { _index_manager->putDocument(id, *doc, serialNum);
                          _index_manager->commit(serialNum,
                                                 emptyDestructorCallback); });
    _writeService.indexFieldWriter().sync();
    return doc;
}

void Fixture::resetIndexManager() {
    _index_manager.reset();
    _index_manager = std::make_unique<IndexManager>(index_dir, IndexConfig(), getSchema(), 1,
                             _reconfigurer, _writeService, _writeService.getMasterExecutor(),
                             TuneFileIndexManager(), TuneFileAttributes(),_fileHeaderContext);
}


void Fixture::assertStats(uint32_t expNumDiskIndexes, uint32_t expNumMemoryIndexes,
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
    EXPECT_EQUAL(expNumDiskIndexes, diskIndexes.size());
    EXPECT_EQUAL(expNumMemoryIndexes, memoryIndexes.size());
    EXPECT_EQUAL(expLastDiskIndexSerialNum, lastDiskIndexSerialNum);
    EXPECT_EQUAL(expLastMemoryIndexSerialNum, lastMemoryIndexSerialNum);
}


TEST_F("requireThatEmptyMemoryIndexIsNotFlushed", Fixture) {
    IIndexCollection::SP sources = f._index_manager->getMaintainer().getSourceCollection();
    EXPECT_EQUAL(1u, sources->getSourceCount());

    f.flushIndexManager();

    sources = f._index_manager->getMaintainer().getSourceCollection();
    EXPECT_EQUAL(1u, sources->getSourceCount());
}

TEST_F("requireThatEmptyMemoryIndexIsFlushedIfSourceSelectorChanged", Fixture)
{
    IIndexCollection::SP sources = f._index_manager->getMaintainer().getSourceCollection();
    EXPECT_EQUAL(1u, sources->getSourceCount());

    f.removeDocument(docid, 42);
    f.flushIndexManager();

    sources = f._index_manager->getMaintainer().getSourceCollection();
    EXPECT_EQUAL(2u, sources->getSourceCount());
}

set<uint32_t> readDiskIds(const string &dir, const string &type) {
    set<uint32_t> ids;
    FastOS_DirectoryScan dir_scan(dir.c_str());
    while (dir_scan.ReadNext()) {
        if (!dir_scan.IsDirectory()) {
            continue;
        }
        string name = dir_scan.GetName();
        const string flush_prefix("index." + type + ".");
        string::size_type pos = name.find(flush_prefix);
        if (pos != 0) {
            continue;
        }
        vespalib::string idString(name.substr(flush_prefix.size()));
        vespalib::asciistream ist(idString);
        uint32_t id;
        ist >> id;
        ids.insert(id);
    }
    return ids;
}

TEST_F("requireThatMemoryIndexIsFlushed", Fixture) {
    FastOS_StatInfo stat;
    {
        f.addDocument(docid);

        IIndexCollection::SP sources =
            f._index_manager->getMaintainer().getSourceCollection();
        EXPECT_EQUAL(1u, sources->getSourceCount());
        EXPECT_EQUAL(1u, sources->getSourceId(0));

        IndexFlushTarget target(f._index_manager->getMaintainer());
        EXPECT_EQUAL(0, target.getLastFlushTime().time());
        vespalib::Executor::Task::UP flushTask;
        f.runAsMaster([&]() { flushTask = target.initFlush(1); });
        flushTask->run();
        EXPECT_TRUE(FastOS_File::Stat("test_data/index.flush.1", &stat));
        EXPECT_EQUAL(stat._modifiedTime, target.getLastFlushTime().time());

        sources = f._index_manager->getMaintainer().getSourceCollection();
        EXPECT_EQUAL(2u, sources->getSourceCount());
        EXPECT_EQUAL(1u, sources->getSourceId(0));
        EXPECT_EQUAL(2u, sources->getSourceId(1));

        set<uint32_t> disk_ids = readDiskIds(index_dir, "flush");
        ASSERT_TRUE(disk_ids.size() == 1);
        EXPECT_EQUAL(1u, *disk_ids.begin());

        FlushStats stats = target.getLastFlushStats();
        EXPECT_EQUAL("test_data/index.flush.1", stats.getPath());
        EXPECT_EQUAL(7u, stats.getPathElementsToLog());
    }
    { // verify last flush time when loading disk index
        f.resetIndexManager();
        IndexFlushTarget target(f._index_manager->getMaintainer());
        EXPECT_EQUAL(stat._modifiedTime, target.getLastFlushTime().time());

        // updated serial number & flush time when nothing to flush
        FastOS_Thread::Sleep(8000);
        fastos::TimeStamp now = fastos::ClockSystem::now();
        vespalib::Executor::Task::UP task;
        f.runAsMaster([&]() { task = target.initFlush(2); });
        EXPECT_TRUE(task.get() == NULL);
        EXPECT_EQUAL(2u, target.getFlushedSerialNum());
        EXPECT_LESS(stat._modifiedTime, target.getLastFlushTime().time());
        EXPECT_APPROX(now.time(), target.getLastFlushTime().time(), 8);
    }
}

TEST_F("requireThatMultipleFlushesGivesMultipleIndexes", Fixture) {
    size_t flush_count = 10;
    for (size_t i = 0; i < flush_count; ++i) {
        f.addDocument(docid);
        f.flushIndexManager();
    }
    set<uint32_t> disk_ids = readDiskIds(index_dir, "flush");
    EXPECT_EQUAL(flush_count, disk_ids.size());
    uint32_t i = 1;
    for (set<uint32_t>::iterator it = disk_ids.begin(); it != disk_ids.end();
         ++it) {
        EXPECT_EQUAL(i++, *it);
    }
}

TEST_F("requireThatMaxFlushesSetsUrgent", Fixture) {
    size_t flush_count = 20;
    for (size_t i = 0; i < flush_count; ++i) {
        f.addDocument(docid);
        f.flushIndexManager();
    }
    IndexFusionTarget target(f._index_manager->getMaintainer());
    EXPECT_TRUE(target.needUrgentFlush());
}

uint32_t getSource(const IIndexCollection &sources, uint32_t id) {
    return sources.getSourceSelector().createIterator()->getSource(id);
}

TEST_F("requireThatPutDocumentUpdatesSelector", Fixture) {
    f.addDocument(docid);
    IIndexCollection::SP sources = f._index_manager->getMaintainer().getSourceCollection();
    EXPECT_EQUAL(1u, getSource(*sources, docid));
    f.flushIndexManager();
    f.addDocument(docid + 1);
    sources = f._index_manager->getMaintainer().getSourceCollection();
    EXPECT_EQUAL(1u, getSource(*sources, docid));
    EXPECT_EQUAL(2u, getSource(*sources, docid + 1));
}

TEST_F("requireThatRemoveDocumentUpdatesSelector", Fixture) {
    Document::UP doc = f.addDocument(docid);
    IIndexCollection::SP sources = f._index_manager->getMaintainer().getSourceCollection();
    EXPECT_EQUAL(1u, getSource(*sources, docid));
    f.flushIndexManager();
    f.removeDocument(docid, ++f._serial_num);
    sources = f._index_manager->getMaintainer().getSourceCollection();
    EXPECT_EQUAL(2u, getSource(*sources, docid));
}

TEST_F("requireThatSourceSelectorIsFlushed", Fixture) {
    f.addDocument(docid);
    f.flushIndexManager();
    FastOS_File file((index_dir + "/index.flush.1/selector.dat").c_str());
    ASSERT_TRUE(file.OpenReadOnlyExisting());
}

TEST_F("requireThatFlushStatsAreCalculated", Fixture) {
    Schema schema(getSchema());
    FieldIndexCollection fic(schema);
    SequencedTaskExecutor invertThreads(2);
    SequencedTaskExecutor pushThreads(2);
    search::memoryindex::DocumentInverter inverter(schema, invertThreads,
                                                   pushThreads);

    uint64_t fixed_index_size = fic.getMemoryUsage().allocatedBytes();
    uint64_t index_size = fic.getMemoryUsage().allocatedBytes() - fixed_index_size;
    /// Must account for both docid 0 being reserved and the extra after.
    uint64_t selector_size = (1) * sizeof(Source);
    EXPECT_EQUAL(index_size, f._index_manager->getMaintainer().getFlushStats().memory_before_bytes -
                             f._index_manager->getMaintainer().getFlushStats().memory_after_bytes);
    EXPECT_EQUAL(0u, f._index_manager->getMaintainer().getFlushStats().disk_write_bytes);
    EXPECT_EQUAL(0u, f._index_manager->getMaintainer().getFlushStats().cpu_time_required);

    Document::UP doc = f.addDocument(docid);
    inverter.invertDocument(docid, *doc);
    invertThreads.sync();
    inverter.pushDocuments(fic,
                           std::shared_ptr<search::IDestructorCallback>());
    pushThreads.sync();
    index_size = fic.getMemoryUsage().allocatedBytes() - fixed_index_size;

    /// Must account for both docid 0 being reserved and the extra after.
    selector_size = (docid + 1) * sizeof(Source);
    EXPECT_EQUAL(index_size,
                 f._index_manager->getMaintainer().getFlushStats().memory_before_bytes -
                 f._index_manager->getMaintainer().getFlushStats().memory_after_bytes);
    EXPECT_EQUAL(selector_size + index_size,
                 f._index_manager->getMaintainer().getFlushStats().disk_write_bytes);
    EXPECT_EQUAL(selector_size * (3+1) + index_size,
                 f._index_manager->getMaintainer().getFlushStats().cpu_time_required);

    doc = f.addDocument(docid + 10);
    inverter.invertDocument(docid + 10, *doc);
    doc = f.addDocument(docid + 100);
    inverter.invertDocument(docid + 100, *doc);
    invertThreads.sync();
    inverter.pushDocuments(fic,
                           std::shared_ptr<search::IDestructorCallback>());
    pushThreads.sync();
    index_size = fic.getMemoryUsage().allocatedBytes() - fixed_index_size;
    /// Must account for both docid 0 being reserved and the extra after.
    selector_size = (docid + 100 + 1) * sizeof(Source);
    EXPECT_EQUAL(index_size,
                 f._index_manager->getMaintainer().getFlushStats().memory_before_bytes -
                 f._index_manager->getMaintainer().getFlushStats().memory_after_bytes);
    EXPECT_EQUAL(selector_size + index_size,
                 f._index_manager->getMaintainer().getFlushStats().disk_write_bytes);
    EXPECT_EQUAL(selector_size * (3+1) + index_size,
                 f._index_manager->getMaintainer().getFlushStats().cpu_time_required);
}

TEST_F("requireThatFusionStatsAreCalculated", Fixture) {
    f.addDocument(docid);
    EXPECT_EQUAL(0u, f._index_manager->getMaintainer().getFusionStats().diskUsage);
    f.flushIndexManager();
    ASSERT_TRUE(f._index_manager->getMaintainer().getFusionStats().diskUsage > 0);
}

TEST_F("requireThatPutDocumentUpdatesSerialNum", Fixture) {
    f._serial_num = 0;
    EXPECT_EQUAL(0u, f._index_manager->getCurrentSerialNum());
    f.addDocument(docid);
    EXPECT_EQUAL(1u, f._index_manager->getCurrentSerialNum());
}

TEST_F("requireThatRemoveDocumentUpdatesSerialNum", Fixture) {
    f._serial_num = 0;
    Document::UP doc = f.addDocument(docid);
    EXPECT_EQUAL(1u, f._index_manager->getCurrentSerialNum());
    f.removeDocument(docid, ++f._serial_num);
    EXPECT_EQUAL(2u, f._index_manager->getCurrentSerialNum());
}

TEST_F("requireThatFlushUpdatesSerialNum", Fixture) {
    f._serial_num = 0;
    f.addDocument(docid);
    EXPECT_EQUAL(1u, f._index_manager->getCurrentSerialNum());
    EXPECT_EQUAL(0u, f._index_manager->getFlushedSerialNum());
    f.flushIndexManager();
    EXPECT_EQUAL(1u, f._index_manager->getCurrentSerialNum());
    EXPECT_EQUAL(1u, f._index_manager->getFlushedSerialNum());
}

TEST_F("requireThatFusionUpdatesIndexes", Fixture) {
    for (size_t i = 0; i < 10; ++i) {
        f.addDocument(docid + i);
        f.flushIndexManager();
    }
    uint32_t ids[] = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
    IIndexCollection::SP
        source_list(f._index_manager->getMaintainer().getSourceCollection());
    EXPECT_EQUAL(10u + 1, source_list->getSourceCount());  // disk + mem
    EXPECT_EQUAL(ids[2], getSource(*source_list, docid + 2));
    EXPECT_EQUAL(ids[6], getSource(*source_list, docid + 6));

    FusionSpec fusion_spec;
    fusion_spec.flush_ids.assign(ids, ids + 4);
    f._index_manager->getMaintainer().runFusion(fusion_spec);

    set<uint32_t> fusion_ids = readDiskIds(index_dir, "fusion");
    EXPECT_EQUAL(1u, fusion_ids.size());
    EXPECT_EQUAL(ids[3], *fusion_ids.begin());

    source_list = f._index_manager->getMaintainer().getSourceCollection();
    EXPECT_EQUAL(10u + 1 - 4 + 1, source_list->getSourceCount());
    EXPECT_EQUAL(0u, getSource(*source_list, docid + 2));
    EXPECT_EQUAL(3u, getSource(*source_list, docid + 6));
}

TEST_F("requireThatFlushTriggersFusion", Fixture) {
    const uint32_t fusion_trigger = 5;
    f.resetIndexManager();

    for (size_t i = 1; i <= fusion_trigger; ++i) {
        f.addDocument(docid);
        f.flushIndexManager();
    }
    IFlushTarget::SP target(new IndexFusionTarget(f._index_manager->getMaintainer()));
    target->initFlush(0)->run();
    f.addDocument(docid);
    f.flushIndexManager();
    set<uint32_t> fusion_ids = readDiskIds(index_dir, "fusion");
    EXPECT_EQUAL(1u, fusion_ids.size());
    EXPECT_EQUAL(5u, *fusion_ids.begin());
    set<uint32_t> flush_ids = readDiskIds(index_dir, "flush");
    EXPECT_EQUAL(1u, flush_ids.size());
    EXPECT_EQUAL(6u, *flush_ids.begin());
}

TEST_F("requireThatFusionTargetIsSetUp", Fixture) {
    f.addDocument(docid);
    f.flushIndexManager();
    f.addDocument(docid);
    f.flushIndexManager();
    IFlushTarget::List lst(f._index_manager->getFlushTargets());
    EXPECT_EQUAL(2u, lst.size());
    IFlushTarget::SP target(lst.at(1));
    EXPECT_EQUAL("memoryindex.fusion", target->getName());
    EXPECT_FALSE(target->needUrgentFlush());
    f.addDocument(docid);
    f.flushIndexManager();
    lst = f._index_manager->getFlushTargets();
    EXPECT_EQUAL(2u, lst.size());
    target = lst.at(1);
    EXPECT_EQUAL("memoryindex.fusion", target->getName());
    EXPECT_TRUE(target->needUrgentFlush());
}

TEST_F("requireThatFusionCleansUpOldIndexes", Fixture) {
    f.addDocument(docid);
    f.flushIndexManager();
    // hold reference to index.flush.1
    IIndexCollection::SP fsc = f._index_manager->getMaintainer().getSourceCollection();

    f.addDocument(docid + 1);
    f.flushIndexManager();

    set<uint32_t> flush_ids = readDiskIds(index_dir, "flush");
    EXPECT_EQUAL(2u, flush_ids.size());

    FusionSpec fusion_spec;
    fusion_spec.flush_ids.push_back(1);
    fusion_spec.flush_ids.push_back(2);
    f._index_manager->getMaintainer().runFusion(fusion_spec);

    flush_ids = readDiskIds(index_dir, "flush");
    EXPECT_EQUAL(1u, flush_ids.size());
    EXPECT_EQUAL(1u, *flush_ids.begin());

    fsc.reset();
    f._index_manager->getMaintainer().removeOldDiskIndexes();
    flush_ids = readDiskIds(index_dir, "flush");
    EXPECT_EQUAL(0u, flush_ids.size());
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

TEST_F("requireThatDiskIndexesAreLoadedOnStartup", Fixture) {
    f.addDocument(docid);
    f.flushIndexManager();
    f._index_manager.reset(0);

    ASSERT_TRUE(indexExists("flush", 1));
    f.resetIndexManager();

    IIndexCollection::SP fsc = f._index_manager->getMaintainer().getSourceCollection();
    EXPECT_EQUAL(2u, fsc->getSourceCount());
    EXPECT_TRUE(contains(*fsc, 1u));
    EXPECT_TRUE(contains(*fsc, 2u));
    EXPECT_EQUAL(1u, getSource(*fsc, docid));
    fsc.reset();


    f.addDocument(docid + 1);
    f.flushIndexManager();
    ASSERT_TRUE(indexExists("flush", 2));
    FusionSpec fusion_spec;
    fusion_spec.flush_ids.push_back(1);
    fusion_spec.flush_ids.push_back(2);
    f._index_manager->getMaintainer().runFusion(fusion_spec);
    f._index_manager.reset(0);

    ASSERT_TRUE(!indexExists("flush", 1));
    ASSERT_TRUE(!indexExists("flush", 2));
    ASSERT_TRUE(indexExists("fusion", 2));
    f.resetIndexManager();

    fsc = f._index_manager->getMaintainer().getSourceCollection();
    EXPECT_EQUAL(2u, fsc->getSourceCount());
    EXPECT_TRUE(contains(*fsc, 0u));
    EXPECT_TRUE(contains(*fsc, 1u));
    EXPECT_EQUAL(0u, getSource(*fsc, docid));
    EXPECT_EQUAL(0u, getSource(*fsc, docid + 1));
    /// Must account for both docid 0 being reserved and the extra after.
    EXPECT_EQUAL(docid + 2, fsc->getSourceSelector().getDocIdLimit());
    fsc.reset();


    f.addDocument(docid + 2);
    f.flushIndexManager();
    f._index_manager.reset(0);

    ASSERT_TRUE(indexExists("fusion", 2));
    ASSERT_TRUE(indexExists("flush", 3));
    f.resetIndexManager();

    fsc = f._index_manager->getMaintainer().getSourceCollection();
    EXPECT_EQUAL(3u, fsc->getSourceCount());
    EXPECT_TRUE(contains(*fsc, 0u));
    EXPECT_TRUE(contains(*fsc, 1u));
    EXPECT_TRUE(contains(*fsc, 2u));
    EXPECT_EQUAL(0u, getSource(*fsc, docid));
    EXPECT_EQUAL(0u, getSource(*fsc, docid + 1));
    EXPECT_EQUAL(1u, getSource(*fsc, docid + 2));
    fsc.reset();
}

TEST_F("requireThatExistingIndexesAreToBeFusionedOnStartup", Fixture) {
    f.addDocument(docid);
    f.flushIndexManager();
    f.addDocument(docid + 1);
    f.flushIndexManager();
    f.resetIndexManager();

    IFlushTarget::SP target(new IndexFusionTarget(f._index_manager->getMaintainer()));
    target->initFlush(0)->run();
    f.addDocument(docid);
    f.flushIndexManager();

    set<uint32_t> fusion_ids = readDiskIds(index_dir, "fusion");
    EXPECT_EQUAL(1u, fusion_ids.size());
    EXPECT_EQUAL(2u, *fusion_ids.begin());
}

TEST_F("requireThatSerialNumberIsWrittenOnFlush", Fixture) {
    f.addDocument(docid);
    f.flushIndexManager();
    FastOS_File file((index_dir + "/index.flush.1/serial.dat").c_str());
    EXPECT_TRUE(file.OpenReadOnly());
}

TEST_F("requireThatSerialNumberIsCopiedOnFusion", Fixture) {
    f.addDocument(docid);
    f.flushIndexManager();
    f.addDocument(docid);
    f.flushIndexManager();
    FusionSpec fusion_spec;
    fusion_spec.flush_ids.push_back(1);
    fusion_spec.flush_ids.push_back(2);
    f._index_manager->getMaintainer().runFusion(fusion_spec);
    FastOS_File file((index_dir + "/index.fusion.2/serial.dat").c_str());
    EXPECT_TRUE(file.OpenReadOnly());
}

TEST_F("requireThatSerialNumberIsReadOnLoad", Fixture) {
    f.addDocument(docid);
    f.flushIndexManager();
    EXPECT_EQUAL(f._serial_num, f._index_manager->getFlushedSerialNum());
    f.resetIndexManager();
    EXPECT_EQUAL(f._serial_num, f._index_manager->getFlushedSerialNum());

    f.addDocument(docid);
    f.flushIndexManager();
    f.addDocument(docid);
    f.flushIndexManager();
    search::SerialNum serial = f._serial_num;
    f.addDocument(docid);
    f.resetIndexManager();
    EXPECT_EQUAL(serial, f._index_manager->getFlushedSerialNum());
}

void crippleFusion(uint32_t fusionId) {
    vespalib::asciistream ost;
    ost << index_dir << "/index.flush." << fusionId << "/serial.dat";
    FastOS_File(ost.str().data()).Delete();
}

TEST_F("requireThatFailedFusionIsRetried", Fixture) {
    f.resetIndexManager();

    f.addDocument(docid);
    f.flushIndexManager();
    f.addDocument(docid);
    f.flushIndexManager();

    crippleFusion(2);

    IndexFusionTarget target(f._index_manager->getMaintainer());
    vespalib::Executor::Task::UP fusionTask = target.initFlush(1);
    fusionTask->run();

    FusionSpec spec = f._index_manager->getMaintainer().getFusionSpec();
    set<uint32_t> fusion_ids = readDiskIds(index_dir, "fusion");
    EXPECT_TRUE(fusion_ids.empty());
    EXPECT_EQUAL(0u, spec.last_fusion_id);
    EXPECT_EQUAL(2u, spec.flush_ids.size());
    EXPECT_EQUAL(1u, spec.flush_ids[0]);
    EXPECT_EQUAL(2u, spec.flush_ids[1]);
}

namespace {

void expectSchemaIndexFields(uint32_t expIndexFields) {
    Schema s;
    s.loadFromFile("test_data/index.flush.1/schema.txt");
    EXPECT_EQUAL(expIndexFields, s.getNumIndexFields());
}

}

TEST_F("require that setSchema updates schema on disk, wiping removed fields", Fixture)
{
    Schema empty_schema;
    f.addDocument(docid);
    f.flushIndexManager();
    TEST_DO(expectSchemaIndexFields(1));
    f.runAsMaster([&]() { f._index_manager->setSchema(empty_schema, ++f._serial_num); });
    TEST_DO(expectSchemaIndexFields(0));
}

TEST_F("require that indexes manager stats can be generated", Fixture)
{
    TEST_DO(f.assertStats(0, 1, 0, 0));
    f.addDocument(1);
    TEST_DO(f.assertStats(0, 1, 0, 1));
    f.flushIndexManager();
    TEST_DO(f.assertStats(1, 1, 1, 1));
    f.addDocument(2);
    TEST_DO(f.assertStats(1, 1, 1, 2));
}

TEST_F("require that compactLidSpace works", Fixture)
{
    Schema empty_schema;
    f.addDocument(1);
    f.addDocument(2);
    f.removeDocument(2);
    auto fsc = f._index_manager->getMaintainer().getSourceCollection();
    EXPECT_EQUAL(3u, fsc->getSourceSelector().getDocIdLimit());
    f.compactLidSpace(2);
    EXPECT_EQUAL(2u, fsc->getSourceSelector().getDocIdLimit());
}

}  // namespace

TEST_MAIN() {
    TEST_DO(removeTestData());
    DummyFileHeaderContext::setCreator("indexmanager_test");
    TEST_RUN_ALL();
    TEST_DO(removeTestData());
}
