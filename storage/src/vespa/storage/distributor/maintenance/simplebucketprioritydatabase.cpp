// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simplebucketprioritydatabase.h"
#include <iostream>
#include <sstream>

namespace storage::distributor {

SimpleBucketPriorityDatabase::~SimpleBucketPriorityDatabase() = default;

void
SimpleBucketPriorityDatabase::clearAllEntriesForBucket(const document::Bucket &bucket)
{
    for (PriorityMap::iterator priIter(_prioritizedBuckets.begin()),
             priEnd(_prioritizedBuckets.end());
         priIter != priEnd;
         ++priIter)
    {
        priIter->second.erase(bucket);
    }
}

void
SimpleBucketPriorityDatabase::setPriority(const PrioritizedBucket& bucket)
{
    clearAllEntriesForBucket(bucket.getBucket());
    if (bucket.requiresMaintenance()) {
        _prioritizedBuckets[bucket.getPriority()].insert(bucket.getBucket());
    }
}

void
SimpleBucketPriorityDatabase::SimpleConstIteratorImpl::initializeBucketIterToFirstAvailableEntry()
{
    _bucketIter = _priorityIter->second.begin();
    if (currentPriorityAtEnd()) {
        increment();
    }
}

bool
SimpleBucketPriorityDatabase::SimpleConstIteratorImpl::atEnd() const
{
    return _priorityIter == _priorityEnd;
}

void
SimpleBucketPriorityDatabase::SimpleConstIteratorImpl::stepWithinCurrentPriority()
{
    ++_bucketIter;
}

bool
SimpleBucketPriorityDatabase::SimpleConstIteratorImpl::currentPriorityAtEnd() const
{
    return _bucketIter == _priorityIter->second.end();
}

void
SimpleBucketPriorityDatabase::SimpleConstIteratorImpl::stepToNextPriority()
{
    ++_priorityIter;
    if (atEnd()) {
        return;
    }
    _bucketIter = _priorityIter->second.begin();
}

void
SimpleBucketPriorityDatabase::SimpleConstIteratorImpl::step()
{
    if (currentPriorityAtEnd()) {
        stepToNextPriority();
    } else {
        stepWithinCurrentPriority();
    }
}

void
SimpleBucketPriorityDatabase::SimpleConstIteratorImpl::increment()
{
    while (!atEnd()) {
        step();
        if (atEnd() || !currentPriorityAtEnd()) {
            break;
        }
    }
}

bool
SimpleBucketPriorityDatabase::SimpleConstIteratorImpl::equal(const ConstIteratorImpl& otherBase) const
{
    const SimpleConstIteratorImpl& other(
            static_cast<const SimpleConstIteratorImpl&>(otherBase));
    if (_priorityIter != other._priorityIter) {
        return false;
    }
    if (atEnd()) {
        return true;
    }
    return _bucketIter == other._bucketIter;
}

PrioritizedBucket
SimpleBucketPriorityDatabase::SimpleConstIteratorImpl::dereference() const
{
    return PrioritizedBucket(*_bucketIter, _priorityIter->first);
}

SimpleBucketPriorityDatabase::const_iterator
SimpleBucketPriorityDatabase::begin() const
{
    return const_iterator(ConstIteratorImplPtr(new SimpleConstIteratorImpl(
                            _prioritizedBuckets.rbegin(),
                            _prioritizedBuckets.rend())));
}

SimpleBucketPriorityDatabase::const_iterator
SimpleBucketPriorityDatabase::end() const
{
    return const_iterator(ConstIteratorImplPtr(new SimpleConstIteratorImpl(
                            _prioritizedBuckets.rend(),
                            _prioritizedBuckets.rend())));
}

std::string
SimpleBucketPriorityDatabase::toString() const
{
    std::ostringstream ss;
    const_iterator i(begin());
    const_iterator e(end());
    for (; i != e; ++i) {
        ss << *i << '\n';
    }
    return ss.str();
}

}
