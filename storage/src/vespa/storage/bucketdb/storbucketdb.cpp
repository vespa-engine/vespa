// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storbucketdb.h"
#include "btree_lockable_map.h"
#include "judymultimap.hpp"
#include "lockablemap.h"

#include <vespa/log/log.h>
LOG_SETUP(".storage.bucketdb.stor_bucket_db");

using document::BucketId;

namespace storage {
namespace bucketdb {

void
StorageBucketInfo::
print(std::ostream& out, bool, const std::string&) const
{
    out << info << ", disk " << disk;
}

bool StorageBucketInfo::operator==(const StorageBucketInfo& b) const {
    return disk == b.disk;
}

bool StorageBucketInfo::operator!=(const StorageBucketInfo& b) const {
    return !(*this == b);
}

bool StorageBucketInfo::operator<(const StorageBucketInfo& b) const {
    return disk < b.disk;
}

std::ostream&
operator<<(std::ostream& out, const StorageBucketInfo& info) {
    info.print(out, false, "");
    return out;
}

namespace {

std::unique_ptr<AbstractBucketMap<StorageBucketInfo>> make_legacy_db_impl() {
    return std::make_unique<LockableMap<JudyMultiMap<StorageBucketInfo>>>();
}

std::unique_ptr<AbstractBucketMap<StorageBucketInfo>> make_btree_db_impl() {
    return std::make_unique<BTreeLockableMap<StorageBucketInfo>>();
}

}

} // bucketdb

StorBucketDatabase::StorBucketDatabase(bool use_btree_db)
    : _impl(use_btree_db ? bucketdb::make_btree_db_impl()
                         : bucketdb::make_legacy_db_impl())
{}

void
StorBucketDatabase::insert(const document::BucketId& bucket,
                           const bucketdb::StorageBucketInfo& entry,
                           const char* clientId)
{
    assert(entry.disk != 0xff);
    bool preExisted;
    return _impl->insert(bucket.toKey(), entry, clientId, false, preExisted);
}

bool
StorBucketDatabase::erase(const document::BucketId& bucket,
                          const char* clientId)
{
    return _impl->erase(bucket.stripUnused().toKey(), clientId, false);
}

bool
StorBucketDatabase::hasBucket(const document::BucketId& bucket) const {
    return acquire_read_guard()->hasKey(bucket.stripUnused().toKey());
}

StorBucketDatabase::WrappedEntry
StorBucketDatabase::get(const document::BucketId& bucket,
                        const char* clientId,
                        Flag flags)
{
    bool createIfNonExisting = (flags & CREATE_IF_NONEXISTING);
    return _impl->get(bucket.stripUnused().toKey(), clientId, createIfNonExisting);
}

size_t StorBucketDatabase::size() const {
    return _impl->size();
}

size_t StorBucketDatabase::getMemoryUsage() const {
    return _impl->getMemoryUsage();
}

vespalib::MemoryUsage StorBucketDatabase::detailed_memory_usage() const noexcept {
    return _impl->detailed_memory_usage();
}

void StorBucketDatabase::showLockClients(vespalib::asciistream& out) const {
    _impl->showLockClients(out);
}

StorBucketDatabase::EntryMap
StorBucketDatabase::getAll(const BucketId& bucketId, const char* clientId) {
    return _impl->getAll(bucketId, clientId);
}

StorBucketDatabase::EntryMap
StorBucketDatabase::getContained(const BucketId& bucketId, const char* clientId) {
    return _impl->getContained(bucketId, clientId);
}

bool StorBucketDatabase::isConsistent(const WrappedEntry& entry) {
    return _impl->isConsistent(entry);
}

void StorBucketDatabase::for_each_chunked(
        std::function<Decision(uint64_t, const bucketdb::StorageBucketInfo&)> func,
        const char* clientId,
        vespalib::duration yieldTime,
        uint32_t chunkSize)
{
    _impl->for_each_chunked(std::move(func), clientId, yieldTime, chunkSize);
}

void StorBucketDatabase::for_each_mutable(
        std::function<Decision(uint64_t, bucketdb::StorageBucketInfo&)> func,
        const char* clientId,
        const key_type& first,
        const key_type& last)
{
    _impl->for_each_mutable(std::move(func), clientId, first, last);
}

void StorBucketDatabase::for_each(
        std::function<Decision(uint64_t, const bucketdb::StorageBucketInfo&)> func,
        const char* clientId,
        const key_type& first,
        const key_type& last)
{
    _impl->for_each(std::move(func), clientId, first, last);
}

std::unique_ptr<bucketdb::ReadGuard<StorBucketDatabase::Entry>>
StorBucketDatabase::acquire_read_guard() const {
    return _impl->acquire_read_guard();
}

template class JudyMultiMap<bucketdb::StorageBucketInfo>;

} // storage
