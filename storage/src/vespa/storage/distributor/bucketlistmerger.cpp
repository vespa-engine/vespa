// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketlistmerger.h"

using namespace storage::distributor;

BucketListMerger::BucketListMerger(const BucketList& newList,
                                   const BucketList& oldList,
                                   uint64_t timestamp)
    : _timestamp(timestamp)
{
    uint32_t i = 0;
    uint32_t j = 0;

    while (i < newList.size() || j < oldList.size()) {
        if (i >= newList.size()) {
            _removedEntries.push_back(oldList[j].first);
            j++;
        } else if (j >= oldList.size()) {
            _addedEntries.push_back(newList[i]);
            i++;
        } else if (newList[i].first.getId() > oldList[j].first.getId()) {
            _removedEntries.push_back(oldList[j].first);
            j++;
        } else if (newList[i].first.getId() < oldList[j].first.getId()) {
            _addedEntries.push_back(newList[i]);
            i++;
        } else {
            if (!(newList[i].second == oldList[j].second)) {
                _addedEntries.push_back(newList[i]);
            }
            i++; j++;
        }
    }
}
