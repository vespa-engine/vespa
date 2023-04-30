// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "data_store_file_chunk_stats.h"
#include <vespa/searchlib/common/i_compactable_lid_space.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/memoryusage.h>
#include <vespa/vespalib/util/time.h>
#include <atomic>
#include <vector>

namespace vespalib { class DataBuffer; }
namespace search {

class IBufferVisitor;

class IDataStoreVisitor
{
public:
    virtual ~IDataStoreVisitor() = default;
    virtual void visit(uint32_t lid, const void *buffer, size_t sz) = 0;
};

class IDataStoreVisitorProgress
{
public:
    virtual ~IDataStoreVisitorProgress() = default;
    virtual void updateProgress(double progress) = 0;
};

/**
 * Simple data storage for byte arrays.
 * A small integer key is associated with each byte array;
 * a zero-sized array is equivalent to a removed key.
 * Changes are held in memory until flush() is called.
 * A sync token is associated with each flush().
 **/
class IDataStore : public common::ICompactableLidSpace
{
public:
    using LidVector = std::vector<uint32_t>;
    /**
     * Construct an idata store.
     * A data store has a base directory. The rest is up to the implementation.
     *
     * @param dirName  The directory that will contain the data file.
     **/
    IDataStore(const vespalib::string & dirName);
     ~IDataStore() override;

    /**
     * Read data from the data store into a buffer.
     * @param lid The local ID associated with the data.
     * @param buffer The buffer where the data will be written
     * @param len On return is set to the number of bytes written to buffer
     * @return true if non-zero-size data was found.
     **/
    virtual ssize_t read(uint32_t lid, vespalib::DataBuffer & buffer) const = 0;
    virtual void read(const LidVector & lids, IBufferVisitor & visitor) const = 0;

    /**
     * Write data to the data store.
     * @param serialNum The official unique reference number for this operation.
     * @param lid The local ID associated with the data.
     * @param buffer The source where the data will be fetched.
     * @param len The number of bytes to fetch from the buffer.
     **/
    virtual void write(uint64_t serialNum, uint32_t lid, const void * buffer, size_t len) = 0;

    /**
     * Remove old data for a key.  Equivalent to write with len==0.
     * @param serialNum The official unique reference number for this operation.
     * @param lid The local ID associated with the data.
     **/
    virtual void remove(uint64_t serialNum, uint32_t lid) = 0;

    /**
     * Flush in-memory data to disk.
     **/
    virtual void flush(uint64_t syncToken) = 0;

    /*
     * Prepare for flushing in-memory data to disk.
     */
    virtual uint64_t initFlush(uint64_t syncToken) = 0;

    /**
     * Calculate memory used by this instance.  During flush() actual
     * memory usage may be approximately twice the reported amount.
     * @return memory usage (in bytes)
     **/
    virtual size_t memoryUsed() const = 0;

    /**
     * Calculates memory that is used for meta data by this instance. Calling
     * flush() does not free this memory.
     * @return memory usage (in bytes)
     **/
    virtual size_t memoryMeta() const = 0;

    /**
     * Calculates how much disk is used
     * @return disk space used.
     */
    virtual size_t getDiskFootprint() const = 0;

    /**
     * Calculates how much disk is used by file headers.
     * @return disk space used.
     */
    virtual size_t getDiskHeaderFootprint() const { return 0u; }
    /**
     * Calculates how much wasted space there is.
     * @return disk bloat.
     */
    virtual size_t getDiskBloat() const = 0;

    /**
     * Calculates how much diskspace can be compacted during a flush.
     * default is to return th ebloat limit, but as some targets have some internal limits
     * to avoid misuse we let the report a more conservative number here if necessary.
     * @return diskspace to be gained.
     */
    virtual size_t getMaxSpreadAsBloat() const = 0;


    /**
     * The sync token used for the last successful flush() operation,
     * or 0 if no flush() has been performed yet.
     * @return Last flushed sync token.
     **/
    virtual uint64_t lastSyncToken() const = 0;

    /*
     * The sync token used for last write operation.
     */
    virtual uint64_t tentativeLastSyncToken() const = 0;

    /**
     * The time of the last flush operation,
     * or 0 if no flush has been performed yet.
     * @return Time of last flush.
     **/
    virtual vespalib::system_time getLastFlushTime() const = 0;

    /**
     * Visit all data found in data store.
     */
    virtual void accept(IDataStoreVisitor &visitor, IDataStoreVisitorProgress &visitorProgress, bool prune) = 0;

    /**
     * Return cost of visiting all data found in data store.
     */
    virtual double getVisitCost() const = 0;

    /*
     * Return brief stats for data store.
     */
    virtual DataStoreStorageStats getStorageStats() const = 0;

    /*
     * Return the memory usage for data store.
     */
    virtual vespalib::MemoryUsage getMemoryUsage() const = 0;

    /*
     * Return detailed stats about underlying files for data store.
     */
    virtual std::vector<DataStoreFileChunkStats> getFileChunkStats() const = 0;

    /**
     * Get the number of entries (including removed IDs
     * or gaps in the local ID sequence) in the data store.
     */
    uint32_t getDocIdLimit() const { return _docIdLimit.load(std::memory_order_acquire); }

    /**
     * Returns the name of the base directory where the data file is stored.
     **/
    const vespalib::string & getBaseDir() const { return _dirName; }

protected:
    void setDocIdLimit(uint32_t docIdLimit) {
        _docIdLimit.store(docIdLimit, std::memory_order_release);
    }
    void updateDocIdLimit(uint32_t docIdLimit) {
        if (docIdLimit > _docIdLimit.load(std::memory_order_relaxed)) {
            setDocIdLimit(docIdLimit);
        }
    }

private:
    std::atomic<uint32_t> _docIdLimit;
    vespalib::string _dirName;
};

} // namespace search

