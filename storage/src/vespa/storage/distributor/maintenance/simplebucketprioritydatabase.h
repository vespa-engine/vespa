// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bucketprioritydatabase.h"
#include <set>
#include <map>

namespace storage::distributor {

class SimpleBucketPriorityDatabase : public BucketPriorityDatabase
{
public:
    virtual ~SimpleBucketPriorityDatabase();
    using Priority = PrioritizedBucket::Priority;

    virtual void setPriority(const PrioritizedBucket&) override;
    virtual const_iterator begin() const override;
    virtual const_iterator end() const override;

    std::string toString() const;

private:
    using BucketSet   = std::set<document::Bucket>;
    using PriorityMap = std::map<Priority, BucketSet>;

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

    void clearAllEntriesForBucket(const document::Bucket &bucket);

    PriorityMap _prioritizedBuckets;
};

}
