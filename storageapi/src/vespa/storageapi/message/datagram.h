// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "visitor.h"
#include <vespa/storageapi/messageapi/storagecommand.h>
#include <vespa/storageapi/messageapi/storagereply.h>
#include <vespa/storageapi/defs.h>

namespace storage::api {

/**
 * @class MapStorageCommand
 * @ingroup message
 *
 * @brief Sends a map of data to a visitor.
 *
 * This is a generic way to transfer data to the visitor data handler.
 * It is for instance used when doing a specialized visitor to gather statistics
 * on usage of document types and namespaces.
 */
class MapVisitorCommand : public StorageCommand {
    vdslib::Parameters _statistics;
public:
    MapVisitorCommand();
    vdslib::Parameters& getData() { return _statistics; };
    const vdslib::Parameters& getData() const { return _statistics; };
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGECOMMAND(MapVisitorCommand, onMapVisitor)
};

/**
 * @class MapStorageReply
 * @ingroup message
 *
 * @brief Confirm that a given map visitor command has been received.
 */
class MapVisitorReply : public StorageReply {
public:
    explicit MapVisitorReply(const MapVisitorCommand&);
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGEREPLY(MapVisitorReply, onMapVisitorReply)
};

/**
 * @class EmptyBucketsCommand
 * @ingroup message
 *
 * @brief Sends a vector of bucket ids to a visitor.
 *
 * This message is used in synchronization to tell the synchronization client
 * that a bucket contains no data at all. This is needed to let the follower be
 * able to delete documents from these buckets, as they would otherwise be
 * ignored by the synch agent.
 */
class EmptyBucketsCommand : public StorageCommand {
    std::vector<document::BucketId> _buckets;
public:
    EmptyBucketsCommand(const std::vector<document::BucketId>&);
    const std::vector<document::BucketId>& getBuckets() const { return _buckets; }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGECOMMAND(EmptyBucketsCommand, onEmptyBuckets)
};

/**
 * @class EmptyBucketsReply
 * @ingroup message
 *
 * @brief Confirm that a given emptybucketscommad has been received.
 */
class EmptyBucketsReply : public StorageReply {
public:
    explicit EmptyBucketsReply(const EmptyBucketsCommand&);
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGEREPLY(EmptyBucketsReply, onEmptyBucketsReply)
};

}
