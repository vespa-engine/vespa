// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/btree/btree.h>
#include <vespa/vespalib/btree/btreebuilder.h>
#include <vespa/vespalib/btree/btreenodeallocator.h>
#include <vespa/vespalib/btree/btreeroot.h>
#include <vespa/vespalib/btree/btreestore.h>

#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/rand48.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

#include <vespa/vespalib/btree/btreenodeallocator.hpp>
#include <vespa/vespalib/btree/btreenode.hpp>
#include <vespa/vespalib/btree/btreenodestore.hpp>
#include <vespa/vespalib/btree/btreeiterator.hpp>
#include <vespa/vespalib/btree/btreeroot.hpp>
#include <vespa/vespalib/btree/btreebuilder.hpp>
#include <vespa/vespalib/btree/btree.hpp>
#include <vespa/vespalib/btree/btreestore.hpp>
#include <vespa/vespalib/btree/btreeaggregator.hpp>

#include <vespa/log/log.h>
LOG_SETUP("btreestress_test");

using MyTree = vespalib::btree::BTree<uint32_t, uint32_t>;
using MyTreeIterator = typename MyTree::Iterator;
using MyTreeConstIterator = typename MyTree::ConstIterator;
using GenerationHandler = vespalib::GenerationHandler;
using vespalib::makeLambdaTask;

struct Fixture
{
    GenerationHandler _generationHandler;
    MyTree _tree;
    MyTreeIterator _writeItr;
    vespalib::ThreadStackExecutor _writer; // 1 write thread
    vespalib::ThreadStackExecutor _readers; // multiple reader threads
    vespalib::Rand48 _rnd;
    uint32_t _keyLimit;
    std::atomic<long> _readSeed;
    std::atomic<long> _doneWriteWork;
    std::atomic<long> _doneReadWork;
    std::atomic<int> _stopRead;
    bool _reportWork;

    Fixture();
    ~Fixture();
    void commit();
    void adjustWriteIterator(uint32_t key);
    void insert(uint32_t key);
    void remove(uint32_t key);

    void readWork(uint32_t cnt);
    void readWork();
    void writeWork(uint32_t cnt);
};


Fixture::Fixture()
    : _generationHandler(),
      _tree(),
      _writeItr(_tree.begin()),
      _writer(1, 128_Ki),
      _readers(4, 128_Ki),
      _rnd(),
      _keyLimit(1000000),
      _readSeed(50),
      _doneWriteWork(0),
      _doneReadWork(0),
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
    commit();
    if (_reportWork) {
        LOG(info,
            "readWork=%ld, writeWork=%ld",
            _doneReadWork.load(), _doneWriteWork.load());
    }
}


void
Fixture::commit()
{
    auto &allocator = _tree.getAllocator();
    allocator.freeze();
    allocator.transferHoldLists(_generationHandler.getCurrentGeneration());
    _generationHandler.incGeneration();
    allocator.trimHoldLists(_generationHandler.getFirstUsedGeneration());
}

void
Fixture::adjustWriteIterator(uint32_t key)
{
    if (_writeItr.valid() && _writeItr.getKey() < key) {
        _writeItr.binarySeek(key);
    } else {
        _writeItr.lower_bound(key);
    }
}

void
Fixture::insert(uint32_t key)
{
    adjustWriteIterator(key);
    assert(!_writeItr.valid() || _writeItr.getKey() >= key);
    if (!_writeItr.valid() || _writeItr.getKey() != key) {
        _tree.insert(_writeItr, key, 0u);
    }
}

void
Fixture::remove(uint32_t key)
{
    adjustWriteIterator(key);
    assert(!_writeItr.valid() || _writeItr.getKey() >= key);
    if (_writeItr.valid() && _writeItr.getKey() == key) {
        _tree.remove(_writeItr);
    }
}


void
Fixture::readWork(uint32_t cnt)
{
    vespalib::Rand48 rnd;
    rnd.srand48(++_readSeed);
    uint32_t i;
    for (i = 0; i < cnt && _stopRead.load() == 0; ++i) {
        auto guard = _generationHandler.takeGuard();
        uint32_t key = rnd.lrand48() % (_keyLimit + 1);
        MyTreeConstIterator itr = _tree.getFrozenView().lowerBound(key);
        assert(!itr.valid() || itr.getKey() >= key);
    }
    _doneReadWork += i;
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
            insert(key);
        } else {
            remove(key);
        }
        commit();
    }
    _doneWriteWork += cnt;
    _stopRead = 1;
    LOG(info, "done %u write work", cnt);
}


TEST_F("Test manual lower bound call", Fixture)
{
    f.insert(1);
    f.remove(2);
    f.insert(1);
    f.insert(5);
    f.insert(4);
    f.remove(3);
    f.remove(5);
    f.commit();
    auto itr = f._tree.getFrozenView().lowerBound(3);
    EXPECT_TRUE(itr.valid());
    EXPECT_EQUAL(4u, itr.getKey());
}

TEST_F("Test single threaded lower_bound reader without updates", Fixture)
{
    f._reportWork = true;
    f.writeWork(10);
    f._stopRead = 0;
    f.readWork(10);
}

TEST_F("Test single threaded lower_bound reader during updates", Fixture)
{
    uint32_t cnt = 1000000;
    f._reportWork = true;
    f._writer.execute(makeLambdaTask([this, cnt]() { f.writeWork(cnt); }));
    f._readers.execute(makeLambdaTask([this]() { f.readWork(); }));
}

TEST_F("Test multithreaded lower_bound reader during updates", Fixture)
{
    uint32_t cnt = 1000000;
    f._reportWork = true;
    f._writer.execute(makeLambdaTask([this, cnt]() { f.writeWork(cnt); }));
    f._readers.execute(makeLambdaTask([this]() { f.readWork(); }));
    f._readers.execute(makeLambdaTask([this]() { f.readWork(); }));
    f._readers.execute(makeLambdaTask([this]() { f.readWork(); }));
    f._readers.execute(makeLambdaTask([this]() { f.readWork(); }));
}

TEST_MAIN() { TEST_RUN_ALL(); }
