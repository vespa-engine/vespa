// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/memfilepersistence/common/types.h>
#include <map>

namespace storage {
namespace memfile {

class BufferCacheCopier;

/**
 * Simple utility to track locations copied between files and to help
 * ensure locations that were shared in the source file will also be shared
 * in the destination file.
 */
class SharedDataLocationTracker
{
public:
    SharedDataLocationTracker(BufferCacheCopier& cacheCopier,
                              Types::DocumentPart part)
        : _cacheCopier(cacheCopier),
          _part(part),
          _trackedLocations()
    {
    }

    /**
     * Get a location to data contained in the destination which points at the
     * exact same data as that given by sourceLocation in the source. Multiple
     * requests to the same source location will return the same destination
     * location.
     */
    DataLocation getOrCreateSharedLocation(DataLocation sourceLocation);
private:
    BufferCacheCopier& _cacheCopier;
    Types::DocumentPart _part;
    std::map<DataLocation, DataLocation> _trackedLocations;
};

/**
 * Interface for copying data between individual MemFile buffer caches.
 */
class BufferCacheCopier
{
    virtual DataLocation doCopyFromSourceToLocal(
            Types::DocumentPart part,
            DataLocation sourceLocation) = 0;
public:
    virtual ~BufferCacheCopier() {}

    /**
     * Copy a given file part location from a source cache into a new location
     * in the destination cache. Returns new location in destination cache.
     * It is assumed that locations returned by this method will be unique.
     */
    DataLocation copyFromSourceToLocal(Types::DocumentPart part,
                                       DataLocation sourceLocation)
    {
        return doCopyFromSourceToLocal(part, sourceLocation);
    }
};

} // memfile
} // storage

