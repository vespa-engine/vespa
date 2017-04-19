// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "ifrozenbuckethandler.h"
#include "ibucketfreezer.h"
#include <vespa/vespalib/util/sync.h>
#include <map>
#include <vector>


namespace searchcorespi { namespace index {class IThreadService; }}

namespace proton {

class IBucketFreezeListener;

/**
 * Controls read and write access to buckets.
 */
class FrozenBucketsMap {
public:
    FrozenBucketsMap();
    ~FrozenBucketsMap();
    IFrozenBucketHandler::ExclusiveBucketGuard::UP acquireExclusiveBucket(document::BucketId bucket);
    void freezeBucket(document::BucketId bucket);
    // Returns true if it was the last one and it was contended.
    bool thawBucket(document::BucketId bucket);
    class ExclusiveBucketGuard : public IFrozenBucketHandler::ExclusiveBucketGuard {
    public:
        ExclusiveBucketGuard(const ExclusiveBucketGuard &) = delete;
        ExclusiveBucketGuard(ExclusiveBucketGuard &&) = delete;
        ExclusiveBucketGuard & operator=(const ExclusiveBucketGuard &) = delete;
        ExclusiveBucketGuard & operator=(ExclusiveBucketGuard &&) = delete;

        ExclusiveBucketGuard(FrozenBucketsMap & handler, document::BucketId & bucketId)
                : IFrozenBucketHandler::ExclusiveBucketGuard(bucketId),
                  _handler(handler)
        { }
        ~ExclusiveBucketGuard() { _handler.releaseExclusiveBucket(getBucket());}
    private:
        FrozenBucketsMap      & _handler;
    };
private:
    void releaseExclusiveBucket(document::BucketId bucket);
    class FrozenBucket {
    public:
        enum Type {Reader, Writer};
        explicit FrozenBucket(Type type=Reader) : _refCount((type==Reader) ? 1 : -1), _notifyWriter(false) { }
        ~FrozenBucket() { assert((_refCount == -1) || (_refCount == 1));}
        void setNotifyWriter() { _notifyWriter = true; }
        bool getNotifyWriter() const { return _notifyWriter; }
        bool isLast() const { return _refCount == 1; }
        bool isExclusive() const { return _refCount == -1; }
        bool hasReaders() const { return _refCount >= 1; }
        void addReader() {
            assert(_refCount >= 1);
            _refCount++;
        }
        void removeReader() {
            assert(_refCount > 1);
            _refCount--;
        }
    private:
        int32_t _refCount;
        bool    _notifyWriter;
    };
    typedef std::map<document::BucketId, FrozenBucket> Map;
    vespalib::Monitor  _lock;
    Map                _map;
};

/**
 * Class that remembers which buckets are frozen and notifies all
 * registered listeners on bucket frozenness changes.
 */
class FrozenBuckets : public IFrozenBucketHandler,
                      public IBucketFreezer
{
    using IThreadService = searchcorespi::index::IThreadService;
    FrozenBucketsMap                     _frozen;
    IThreadService                      &_masterThread;
    std::vector<IBucketFreezeListener *> _listeners;

    void notifyThawed(document::BucketId bucket);
public:
    FrozenBuckets(IThreadService &masterThread);
    virtual ~FrozenBuckets();

    virtual ExclusiveBucketGuard::UP acquireExclusiveBucket(document::BucketId bucket) override;
    virtual void freezeBucket(document::BucketId bucket) override;
    virtual void thawBucket(document::BucketId bucket) override;
    virtual void addListener(IBucketFreezeListener *listener) override;
    virtual void removeListener(IBucketFreezeListener *listener) override;
};
    
} // namespace proton
