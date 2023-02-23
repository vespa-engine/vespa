// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include "queue.h"
#include <vespa/vespalib/util/thread.h>
#include <atomic>

namespace vespalib {

typedef std::vector<void *> MemListImpl;
typedef MemListImpl * MemList;
typedef vespalib::Queue<MemList>      MemQueue;

struct RunWithStopFlag {
    virtual void run(std::atomic<bool> &stop_flag) = 0;
    void start(vespalib::ThreadPool &pool, std::atomic<bool> &stop_flag) {
        pool.start([this,&stop_flag](){run(stop_flag);});
    }
    virtual ~RunWithStopFlag() = default;
};

class Consumer : public RunWithStopFlag {
private:
    MemQueue _queue;
    bool     _inverse;
    uint64_t _operations;
    virtual void consume(void *) = 0;
public:
    Consumer(uint32_t maxQueue, bool inverse);
    virtual ~Consumer();
    void enqueue(const MemList &mem) { _queue.enqueue(mem); }
    void close() { _queue.close(); }
    void run(std::atomic<bool> &stop_flag) override;
    uint64_t operations() const { return _operations; }
};

class Producer : public RunWithStopFlag {
private:
    Consumer & _target;
    uint32_t   _cnt;
    uint64_t   _operations;
    virtual void * produce() = 0;
public:
    Producer(uint32_t cnt, Consumer &target);
    virtual ~Producer();
    void run(std::atomic<bool> &stop_flag) override;
    uint64_t operations() const { return _operations; }
};

class ProducerConsumer : public RunWithStopFlag {
private:
    uint32_t _cnt;
    bool     _inverse;
    uint64_t _operationsConsumed;
    uint64_t _operationsProduced;
    virtual void * produce() = 0;
    virtual void consume(void *) = 0;
public:
    ProducerConsumer(uint32_t cnt, bool inverse);
    virtual ~ProducerConsumer();
    void run(std::atomic<bool> &stop_flag) override;
    uint64_t operationsConsumed() const { return _operationsConsumed; }
    uint64_t operationsProduced() const { return _operationsProduced; }
};

}

