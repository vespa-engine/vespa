// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bucketprioritydatabase.h"
#include <set>
#include <map>

namespace storage {
namespace distributor {

class SimpleBucketPriorityDatabase : public BucketPriorityDatabase
{
public:
    virtual ~SimpleBucketPriorityDatabase();
    typedef PrioritizedBucket::Priority Priority;

    virtual void setPriority(const PrioritizedBucket&) override;
    virtual const_iterator begin() const override;
    virtual const_iterator end() const override;

    std::string toString() const;

private:
    typedef std::set<document::BucketId> BucketSet;
    typedef std::map<Priority, BucketSet> PriorityMap;

    class SimpleConstIteratorImpl : public ConstIteratorImpl
    {
        PriorityMap::const_reverse_iterator _priorityIter;
        PriorityMap::const_reverse_iterator _priorityEnd;
        BucketSet::const_iterator _bucketIter;
    public:
        SimpleConstIteratorImpl(PriorityMap::const_reverse_iterator first,
                                PriorityMap::const_reverse_iterator end)
            : _priorityIter(first),
              _priorityEnd(end),
              _bucketIter()
        {
            if (!atEnd()) {
                initializeBucketIterToFirstAvailableEntry();
            }
        }
        SimpleConstIteratorImpl(const SimpleConstIteratorImpl&) = delete;
        SimpleConstIteratorImpl& operator=(const SimpleConstIteratorImpl&) = delete;
    private:
        void initializeBucketIterToFirstAvailableEntry();

        bool atEnd() const;
        void stepWithinCurrentPriority();
        bool currentPriorityAtEnd() const;
        void stepToNextPriority();
        void step();

        void increment() override;
        bool equal(const ConstIteratorImpl& other) const override;
        PrioritizedBucket dereference() const override;
    };

    void clearAllEntriesForBucket(const document::BucketId& bucketId);

    PriorityMap _prioritizedBuckets;
};

}
}
