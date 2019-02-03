// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::StorageBucketDBInitializer
 * \ingroup bucketdb
 *
 * \brief Initializes the bucket database on the storage node.
 *
 * The storage bucket DB is responsible for initializing the bucket database on
 * the storage node. This used to be the task of the bucket manager, but to
 * make the implementation cleaner, the logic for this has been separated.
 *
 * This works as follows:
 *
 * 1. When component is started (onOpen), partition states should already have
 * been aquired from the SPI and made available to this class. Requests for
 * listing buckets will be sent to all partitions. Background thread will be
 * started to avoid doing processes in thread sending replies.
 *
 * 2. Upon receiving bucket lists into background thread, the bucket database
 * will be populated with buckets. Bucket information may at this point be
 * invalid or not, depending on persistence provider. Providers that can list
 * cheaply but where getting info is more expensive, will likely want to return
 * invalid entries as the node can start handling load as fast as bucket lists
 * is known. Providers who gets info and bucket lists equally cheap will likely
 * prefer to give info at once to avoid the read step.
 *
 * 3. Upon receiving the last bucket list, the background thread will be started
 * to do remaining work.
 *
 * 4. Background thread will iterate through the bucket database, issuing
 * bucket info requests for all buckets that have invalid bucket info. Once the
 * whole bucket database has been iterated and there are no longer pending
 * operations, initialization is complete, and node will be tagged in up state.
 */

#pragma once

#include <atomic>
#include <vespa/vespalib/util/document_runnable.h>
#include <vespa/metrics/metrics.h>
#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/storage/bucketdb/minimumusedbitstracker.h>
#include <vespa/storage/common/bucketmessages.h>
#include <vespa/storage/common/doneinitializehandler.h>
#include <vespa/storage/common/servicelayercomponent.h>
#include <vespa/storage/common/storagelink.h>
#include <vespa/storageframework/generic/status/htmlstatusreporter.h>
#include <vespa/storageframework/generic/clock/timer.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <vespa/vdslib/state/nodestate.h>
#include <vespa/config/subscription/configuri.h>
#include <list>
#include <unordered_map>
#include <mutex>
#include <condition_variable>

namespace storage {

struct BucketReadState;

class StorageBucketDBInitializer : public StorageLink,
                                   public framework::HtmlStatusReporter,
                                   private framework::Runnable
{
    typedef uint16_t Disk;
    typedef vespalib::hash_map<api::StorageMessage::Id, Disk> IdDiskMap;

    struct Config {
        // List priority should be larger than info priority.
        uint16_t _listPriority;
        uint16_t _infoReadPriority;
        // When going below this amount of pending, send more until we reach max
        uint16_t _minPendingInfoReadsPerDisk;
        uint16_t _maxPendingInfoReadsPerDisk;

        Config(const config::ConfigUri & configUri);
    };
    struct System {
        DoneInitializeHandler& _doneInitializeHandler;
        ServiceLayerComponent _component;
        const spi::PartitionStateList& _partitions;
        const ContentBucketSpaceRepo& _bucketSpaceRepo;
        uint32_t _nodeIndex;
        lib::NodeState _nodeState; // Disk info for ideal state calculations
        framework::Thread::UP _thread;

        System(const spi::PartitionStateList&,
               DoneInitializeHandler& doneInitializeHandler,
               ServiceLayerComponentRegister&,
               const Config&);
        ~System();

        StorBucketDatabase &getBucketDatabase(document::BucketSpace bucketSpace) const;
    };
    struct Metrics : public metrics::MetricSet {
        metrics::LongCountMetric _wrongDisk;
        metrics::LongCountMetric _insertedCount;
        metrics::LongCountMetric _joinedCount;
        metrics::LongCountMetric _infoReadCount;
        metrics::LongCountMetric _infoSetByLoad;
        metrics::LongCountMetric _dirsListed;
        framework::MilliSecTimer _startTime;
        metrics::LongAverageMetric _listLatency;
        metrics::LongAverageMetric _initLatency;

        Metrics(framework::Component&);
        ~Metrics();
    };
    struct GlobalState {
        vespalib::hash_map<api::StorageMessage::Id, ReadBucketList::SP> _lists;
        vespalib::hash_map<api::StorageMessage::Id, InternalBucketJoinCommand::SP> _joins;
        IdDiskMap _infoRequests;
        std::list<api::StorageMessage::SP> _replies;
        uint64_t _insertedCount;
        uint64_t _infoReadCount;
        uint64_t _infoSetByLoad;
        uint64_t _dirsListed;
        uint32_t _dirsToList;
        bool _gottenInitProgress;
        std::atomic<bool> _doneListing;
        bool _doneInitializing;
            // This lock is held while the worker thread is working, such that
            // status retrieval can lock it. Listing part only grabs it when
            // needed to supporting listing in multiple threads
        mutable std::mutex       _workerLock;
        std::condition_variable  _workerCond;
            // This lock protects the reply list.
        std::mutex               _replyLock;

        GlobalState();
        ~GlobalState();
    };

public:
    using BucketSpaceReadState = std::unordered_map<document::BucketSpace,
            std::unique_ptr<BucketReadState>, document::BucketSpace::hash>;
    using ReadState = std::vector<std::unique_ptr<BucketSpaceReadState>>;

private:
    Config _config;
    System _system;
    Metrics _metrics;
    GlobalState _state;
    ReadState _readState;

public:
    StorageBucketDBInitializer(const config::ConfigUri&,
                               const spi::PartitionStateList&,
                               DoneInitializeHandler&,
                               ServiceLayerComponentRegister&);
    ~StorageBucketDBInitializer();

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    void onOpen() override;
    void onClose() override;

    void run(framework::ThreadHandle&) override;

    bool onDown(const std::shared_ptr<api::StorageMessage>&) override;
    bool onInternalReply(const std::shared_ptr<api::InternalReply>&) override;

    void handleReadBucketListReply(ReadBucketListReply&);
    void handleReadBucketInfoReply(ReadBucketInfoReply&);
    void handleInternalBucketJoinReply(InternalBucketJoinReply&);

    /** Status implementation. */
    void reportHtmlStatus(std::ostream&, const framework::HttpUrlPath&) const override;

    // The below functions should only be called by the class itself, but they
    // are left public for easability of access for unit tests and anonymous
    // classes defined in implementation.

    /** Get the path of a given directory. */
    std::string getPathName(std::vector<uint32_t>& path,
                            const document::BucketId* = 0) const;
    /** Process a given file found through listing files on disk */
    bool processFile(std::vector<uint32_t>& path, const std::string& pathName,
                     const std::string& name);
    /**
     * Find what bucket identifier file corresponds to.
     * Invalid bucket indicates none. (Invalid file name)
     */
    document::BucketId extractBucketId(const std::string& name) const;
    /**
     * Handle that the bucket might have been found in the wrong position.
     * Returns true if we should attepmt to register the bucket.
     */
    bool handleBadLocation(const document::BucketId&,
                           std::vector<uint32_t>& path);
    /** Register a bucket in the bucket database. */
    void registerBucket(const document::Bucket &bucket,
                        const lib::Distribution &distribution,
                        spi::PartitionId,
                        api::BucketInfo bucketInfo);
    /**
     * Sends more read bucket info to a given disk. Lock must already be taken.
     * Will be released by function prior to sending messages down.
     */
    void sendReadBucketInfo(spi::PartitionId, document::BucketSpace bucketSpace);
    /** Check whether initialization is complete. Should hold lock to call it.*/
    void checkIfDone();

    /** Calculate minimum progress from all disks' bucket db iterators */
    double calculateMinProgressFromDiskIterators() const;
    /** Calculate how far we have progressed initializing. */
    double calcInitProgress() const;
    /** Update node state if init progress have changed enough. */
    void updateInitProgress() const;
    /** Handle that we're done listing buckets. */
    void handleListingCompleted();

    /** Used for unit tests to see that stuff has happened. */
    virtual const Metrics& getMetrics() const { return _metrics; }


    class BucketProgressCalculator
    {
    public:
        /**
         * Estimate progress into the total bucket space.
         * Done by taking reverse bucket key, shifting away unused bits and
         * dividing the result by 2**used bits to get approximate progress.
         * @param bid Current bucket space iterator/cursor.
         */
        static double calculateProgress(const document::BucketId& bid);
    };
};

} // storage
