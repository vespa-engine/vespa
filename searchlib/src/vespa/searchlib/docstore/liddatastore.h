// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "idatastore.h"

namespace search {

/**
 * Factor out stuff common to MultiDataStore and SimpleDatastore
 **/
class LidDataStore : public IDataStore
{
public:
    /**
     * Construct an idata store.
     * A data store has a base directory. The rest is up to the implementation.
     *
     * @param dirName  The directory that will contain the data file.
     **/
    LidDataStore(const vespalib::string & dirName) : IDataStore(dirName), _lastSyncToken(0) { }


    /**
     * The sync token used for the last successful flush() operation,
     * or 0 if no flush() has been performed yet.
     * @return Last flushed sync token.
     **/
    virtual uint64_t lastSyncToken() const { return _lastSyncToken; }

    virtual size_t getDiskBloat() const { return 0; }

    /**
     * Flush all in-memory data to disk.
     **/
    virtual void flushAll(uint64_t syncToken) {
        flush(syncToken);
    }

    /**
     * Get the number of entries (including removed IDs
     * or gaps in the local ID sequence) in the data store.
     * @return The next local ID expected to be used
     */
//    uint64_t nextId() const { return _nextId; }


protected:
    void setLastSyncToken(uint64_t last) { _lastSyncToken = last; }
//    void setNextId(uint64_t id) { _nextId = id; }

private:
    uint64_t    _lastSyncToken;
//    uint64_t    _nextId;
};

} // namespace search

