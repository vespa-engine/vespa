// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <set>
#include <map>
#include <vespa/storage/distributor/maintenance/bucketprioritydatabase.h>

namespace storage {
namespace distributor {

class SimpleBucketPriorityDatabase : public BucketPriorityDatabase
{
public:
    virtual ~SimpleBucketPriorityDatabase();
    typedef PrioritizedBucket::Priority Priority;

    virtual void setPriority(const PrioritizedBucket&);

    virtual const_iterator begin() const;

    virtual const_iterator end() const;

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
    private:
        SimpleConstIteratorImpl(const SimpleConstIteratorImpl&);
        SimpleConstIteratorImpl& operator=(const SimpleConstIteratorImpl&);

        void initializeBucketIterToFirstAvailableEntry();

        bool atEnd() const;
        void stepWithinCurrentPriority();
        bool currentPriorityAtEnd() const;
        void stepToNextPriority();
        void step();

        virtual void increment();

        virtual bool equal(const ConstIteratorImpl& other) const;

        virtual PrioritizedBucket dereference() const;
    };

    void clearAllEntriesForBucket(const document::BucketId& bucketId);

    PriorityMap _prioritizedBuckets;
};

}
}

