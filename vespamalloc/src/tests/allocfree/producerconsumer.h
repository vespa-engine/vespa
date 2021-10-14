// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include "queue.h"
#include <vespa/fastos/thread.h>

namespace vespalib {

typedef std::vector<void *> MemListImpl;
typedef MemListImpl * MemList;
typedef vespalib::Queue<MemList>      MemQueue;

class Consumer : public FastOS_Runnable {
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
    void Run(FastOS_ThreadInterface *t, void *) override;
    uint64_t operations() const { return _operations; }
};

class Producer : public FastOS_Runnable {
private:
    Consumer & _target;
    uint32_t   _cnt;
    uint64_t   _operations;
    virtual void * produce() = 0;
public:
    Producer(uint32_t cnt, Consumer &target);
    virtual ~Producer();
    void Run(FastOS_ThreadInterface *t, void *) override;
    uint64_t operations() const { return _operations; }
};

class ProducerConsumer : public FastOS_Runnable {
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
    void Run(FastOS_ThreadInterface *t, void *) override;
    uint64_t operationsConsumed() const { return _operationsConsumed; }
    uint64_t operationsProduced() const { return _operationsProduced; }
};

}

