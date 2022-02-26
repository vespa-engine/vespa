// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "producerconsumer.h"
#include <vespa/vespalib/testkit/testapp.h>
#include <map>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP("allocfree_test");

using vespalib::Consumer;
using vespalib::Producer;
using vespalib::ProducerConsumer;

TEST_SETUP(Test);

//-----------------------------------------------------------------------------

class FreeWorker : public Consumer {
public:
    FreeWorker(uint32_t maxQueue, bool inverse)
        : Consumer (maxQueue, inverse) {}
    ~FreeWorker() override;
private:
    void consume(void * p) override { free(p); }
};

FreeWorker::~FreeWorker() = default;

//-----------------------------------------------------------------------------

class MallocWorker : public Producer {
public:
    MallocWorker(uint32_t size, uint32_t cnt, FreeWorker &target)
        : Producer(cnt, target), _size(size) {}
    ~MallocWorker() override;
private:
    uint32_t _size;
    void * produce() override { return malloc(_size); }
};

MallocWorker::~MallocWorker() = default;

//-----------------------------------------------------------------------------

class MallocFreeWorker : public ProducerConsumer {
public:
    MallocFreeWorker(uint32_t size, uint32_t cnt, bool inverse)
        : ProducerConsumer(cnt, inverse), _size(size) { }
    ~MallocFreeWorker() override;
private:
    uint32_t _size;

    void * produce() override { return malloc(_size); }
    void consume(void * p) override { free(p); }
};

MallocFreeWorker::~MallocFreeWorker() = default;

//-----------------------------------------------------------------------------

int Test::Main() {
    int duration = 10;
    int numCrossThreadAlloc(2);
    int numSameThreadAlloc(2);
    if (_argc > 1) {
        duration = atoi(_argv[1]);
    }
    if (_argc > 2) {
        numCrossThreadAlloc = atoi(_argv[2]);
    }
    if (_argc > 3) {
        numSameThreadAlloc = atoi(_argv[3]);
    }
    TEST_INIT("allocfree_test");

    FastOS_ThreadPool pool(128000);

    std::map<int, std::shared_ptr<FreeWorker> > freeWorkers;
    std::map<int, std::shared_ptr<MallocWorker> > mallocWorkers;
    std::map<int, std::shared_ptr<MallocFreeWorker> > mallocFreeWorkers;
    for (int i(0); i < numCrossThreadAlloc; i++) {
        freeWorkers[i] = std::shared_ptr<FreeWorker>(new FreeWorker(1024, (i%2) ? true : false));
        mallocWorkers[i] = std::shared_ptr<MallocWorker>(new MallocWorker(400, 256, *freeWorkers[i]));
    }
    for(int i(0); i < numSameThreadAlloc; i++) {
        mallocFreeWorkers[i] = std::shared_ptr<MallocFreeWorker>(new MallocFreeWorker(200, 16, (i%2) ? true : false));
    }


    for(std::map<int, std::shared_ptr<FreeWorker> >::iterator it(freeWorkers.begin()), mt(freeWorkers.end()); it != mt; it++) {
        ASSERT_TRUE(pool.NewThread(it->second.get(), NULL) != NULL);
    }
    for(std::map<int, std::shared_ptr<MallocWorker> >::iterator it(mallocWorkers.begin()), mt(mallocWorkers.end()); it != mt; it++) {
        ASSERT_TRUE(pool.NewThread(it->second.get(), NULL) != NULL);
    }
    for(std::map<int, std::shared_ptr<MallocFreeWorker> >::iterator it(mallocFreeWorkers.begin()), mt(mallocFreeWorkers.end()); it != mt; it++) {
        ASSERT_TRUE(pool.NewThread(it->second.get(), NULL) != NULL);
    }

    for (; duration > 0; --duration) {
        LOG(info, "%d seconds left...", duration);
        std::this_thread::sleep_for(1s);
    }
    pool.Close();
    size_t numFreeOperations(0);
    size_t numMallocOperations(0);
    size_t numSameThreadMallocFreeOperations(0);
    for(std::map<int, std::shared_ptr<FreeWorker> >::iterator it(freeWorkers.begin()), mt(freeWorkers.end()); it != mt; it++) {
        numFreeOperations += it->second->operations();
    }
    for(std::map<int, std::shared_ptr<MallocWorker> >::iterator it(mallocWorkers.begin()), mt(mallocWorkers.end()); it != mt; it++) {
        numMallocOperations += it->second->operations();
    }
    for(std::map<int, std::shared_ptr<MallocFreeWorker> >::iterator it(mallocFreeWorkers.begin()), mt(mallocFreeWorkers.end()); it != mt; it++) {
        numSameThreadMallocFreeOperations += it->second->operationsConsumed();
    }
    EXPECT_EQUAL(numFreeOperations, numMallocOperations);
    const size_t numCrossThreadMallocFreeOperations(numMallocOperations);

    fprintf(stderr, "Did %lu Cross thread malloc/free operations\n", numCrossThreadMallocFreeOperations);
    fprintf(stderr, "Did %lu Same thread malloc/free operations\n", numSameThreadMallocFreeOperations);
    fprintf(stderr, "Did %lu Total operations\n", numCrossThreadMallocFreeOperations + numSameThreadMallocFreeOperations);

    TEST_DONE();
}
