// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "producerconsumer.h"

namespace vespalib {

Consumer::Consumer(uint32_t maxQueue, bool inverse) :
   _queue(NULL, maxQueue),
   _inverse(inverse),
   _operations(0)
{
}

Consumer::~Consumer()
{
}

Producer::Producer(uint32_t cnt, Consumer &target) :
    _target(target),
    _cnt(cnt),
    _operations(0)
{
}

Producer::~Producer()
{
}

ProducerConsumer::ProducerConsumer(uint32_t cnt, bool inverse) :
    _cnt(cnt),
    _inverse(inverse),
    _operationsConsumed(0),
    _operationsProduced(0)
{
}

ProducerConsumer::~ProducerConsumer()
{
}


void Consumer::run(std::atomic<bool> &) {
    for (;;) {
        MemList ml = _queue.dequeue();
        if (ml == NULL) {
            return;
        }
        if (_inverse) {
            for (uint32_t i = ml->size(); i > 0; --i) {
                consume((*ml)[i - 1]);
                _operations++;
            }
        } else {
            for (uint32_t i = 0; i < ml->size(); ++i) {
                consume((*ml)[i]);
                _operations++;
            }
        }
        delete ml;
    }
}

void Producer::run(std::atomic<bool> &stop_flag) {
    while (!stop_flag.load(std::memory_order_relaxed)) {
        MemList ml = new MemListImpl();
        for (uint32_t i = 0; i < _cnt; ++i) {
            ml->push_back(produce());
            _operations++;
        }
        _target.enqueue(ml);
    }
    _target.close();
}

void ProducerConsumer::run(std::atomic<bool> &stop_flag) {
    while (!stop_flag.load(std::memory_order_relaxed)) {
        MemListImpl ml;
        for (uint32_t i = 0; i < _cnt; ++i) {
            ml.push_back(produce());
            _operationsProduced++;
        }
        if (_inverse) {
            for (uint32_t i = ml.size(); i > 0; --i) {
                consume(ml[i - 1]);
                _operationsConsumed++;
            }
        } else {
            for (uint32_t i = 0; i < ml.size(); ++i) {
                consume(ml[i]);
                _operationsConsumed++;
            }
        }
    }
}

}
