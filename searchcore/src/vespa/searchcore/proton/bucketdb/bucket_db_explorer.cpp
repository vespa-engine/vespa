// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucket_db_explorer.h"

#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/stllike/asciistream.h>

using document::BucketId;
using vespalib::slime::Cursor;
using vespalib::slime::Inserter;

namespace proton {

namespace {

vespalib::string
bucketIdToString(const BucketId &bucketId)
{
    vespalib::asciistream stream;
    stream << "0x" << vespalib::hex << vespalib::setw(sizeof(BucketId::Type)*2)
           << vespalib::setfill('0') << bucketId.getId();
    return stream.str();
}

vespalib::string
checksumToString(storage::spi::BucketChecksum checksum)
{
    vespalib::asciistream stream;
    stream << "0x" << vespalib::hex << checksum;
    return stream.str();
}

void
convertBucketsToSlime(const BucketDB &bucketDb, Cursor &array)
{
    for (auto itr = bucketDb.begin(); itr != bucketDb.end(); ++itr) {
        Cursor &object = array.addObject();
        object.setString("id", bucketIdToString(itr->first));
        const bucketdb::BucketState &state = itr->second;
        object.setString("checksum", checksumToString(state.getChecksum()));
        object.setLong("readyCount", state.getReadyCount());
        object.setLong("notReadyCount", state.getNotReadyCount());
        object.setLong("removedCount", state.getRemovedCount());
        object.setLong("readyDocSizes", state.getReadyDocSizes());
        object.setLong("notReadyDocSizes", state.getNotReadyDocSizes());
        object.setLong("removedDocSizes", state.getRemovedDocSizes());
        object.setBool("active", state.isActive());
    }
}

}

BucketDBExplorer::BucketDBExplorer(bucketdb::Guard bucketDb)
    : _bucketDb(std::move(bucketDb))
{
}

BucketDBExplorer::~BucketDBExplorer() = default;

void
BucketDBExplorer::get_state(const Inserter &inserter, bool full) const
{
    Cursor &object = inserter.insertObject();
    if (full) {
        object.setLong("numBuckets", _bucketDb->size());
        convertBucketsToSlime(*_bucketDb, object.setArray("buckets"));
    } else {
        object.setLong("numBuckets", _bucketDb->size());
    }
}

} // namespace proton
