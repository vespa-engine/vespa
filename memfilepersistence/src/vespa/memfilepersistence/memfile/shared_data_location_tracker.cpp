// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "shared_data_location_tracker.h"

namespace storage {
namespace memfile {

DataLocation
SharedDataLocationTracker::getOrCreateSharedLocation(
        DataLocation sourceLocation)
{
    DataLocation& bufferedLoc(_trackedLocations[sourceLocation]);
    if (!bufferedLoc.valid()) {
       bufferedLoc = _cacheCopier.copyFromSourceToLocal(_part, sourceLocation);
    }
    return bufferedLoc;
}


} // memfile
} // storage
