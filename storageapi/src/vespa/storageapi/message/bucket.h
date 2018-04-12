// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @file bucketinfo.h
 *
 * Bucket related commands.
 */

#pragma once

#include <vespa/storageapi/messageapi/bucketcommand.h>
#include <vespa/storageapi/messageapi/bucketreply.h>
#include <vespa/storageapi/messageapi/bucketinfocommand.h>
#include <vespa/storageapi/messageapi/bucketinforeply.h>
#include <vespa/storageapi/messageapi/maintenancecommand.h>
#include <vespa/document/base/globalid.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/storageapi/defs.h>

namespace document { class DocumentTypeRepo; }

namespace storage {
namespace api {

/**
 * @class CreateBucketCommand
 * @ingroup message
 *
 * @brief Command for creating a new bucket on a storage node.
 */
class CreateBucketCommand : public MaintenanceCommand {
    bool _active;

public:
    explicit CreateBucketCommand(const document::Bucket &bucket);
    void setActive(bool active) { _active = active; }
    bool getActive() const { return _active; }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGECOMMAND(CreateBucketCommand, onCreateBucket)
};

/**
 * @class CreateBucketReply
 * @ingroup message
 *
 * @brief Reply of a create bucket command.
 */
class CreateBucketReply : public BucketInfoReply {
public:
    explicit CreateBucketReply(const CreateBucketCommand& cmd);
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGEREPLY(CreateBucketReply, onCreateBucketReply);
};

/**
 * @class DeleteBucketCommand
 * @ingroup message
 *
 * @brief Command for deleting a bucket from one or more storage nodes.
 */
class DeleteBucketCommand : public MaintenanceCommand {
    BucketInfo _info;
public:
    explicit DeleteBucketCommand(const document::Bucket &bucket);

    const BucketInfo& getBucketInfo() const { return _info; }
    void setBucketInfo(const BucketInfo& info) { _info = info; }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGECOMMAND(DeleteBucketCommand, onDeleteBucket)
};

/**
 * @class DeleteBucketReply
 * @ingroup message
 *
 * @brief Reply of a delete bucket command.
 */
class DeleteBucketReply : public BucketInfoReply {
public:
    explicit DeleteBucketReply(const DeleteBucketCommand& cmd);
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGEREPLY(DeleteBucketReply, onDeleteBucketReply)
};


/**
 * @class MergeBucketCommand
 * @ingroup message
 *
 * @brief Merge a bucket
 *
 * Merges given bucket copies, held on the given node list. A maximum timestamp
 * should be given, such that the buckets may be used during merge. If not
 * given, storage will set current time for it, but distributors should really
 * set it, as they have the reference clock for a bucket.
 *
 * An optional "only for source" node list can be provided. In this case, the
 * nodes in that list are only used for sources in the merge, and never as
 * targets, even if they are missing documents from the other nodes.
 *
 */
class MergeBucketCommand : public MaintenanceCommand {
public:
    struct Node {
        uint16_t index;
        bool sourceOnly;

        Node(uint16_t index_, bool sourceOnly_ = false)
            : index(index_), sourceOnly(sourceOnly_) {}

        bool operator==(const Node& n) const
            { return (index == n.index && sourceOnly == n.sourceOnly); }
    };

private:
    std::vector<Node> _nodes;
    Timestamp _maxTimestamp;
    uint32_t _clusterStateVersion;
    std::vector<uint16_t> _chain;

public:
    MergeBucketCommand(const document::Bucket &bucket,
                       const std::vector<Node>&,
                       Timestamp maxTimestamp,
                       uint32_t clusterStateVersion = 0,
                       const std::vector<uint16_t>& chain = std::vector<uint16_t>());
    ~MergeBucketCommand();

    const std::vector<Node>& getNodes() const { return _nodes; }
    Timestamp getMaxTimestamp() const { return _maxTimestamp; }
    const std::vector<uint16_t>& getChain() const { return _chain; }
    uint32_t getClusterStateVersion() const { return _clusterStateVersion; }
    void setClusterStateVersion(uint32_t version) { _clusterStateVersion = version; }
    void setChain(const std::vector<uint16_t>& chain) { _chain = chain; }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGECOMMAND(MergeBucketCommand, onMergeBucket)
};

std::ostream&
operator<<(std::ostream& out, const MergeBucketCommand::Node& n);

/**
 * @class MergeBucketReply
 * @ingroup message
 *
 * @brief Reply of a merge bucket command.
 */
class MergeBucketReply : public BucketReply {
public:
    typedef MergeBucketCommand::Node Node;

private:
    std::vector<Node> _nodes;
    Timestamp _maxTimestamp;
    uint32_t _clusterStateVersion;
    std::vector<uint16_t> _chain;

public:
    explicit MergeBucketReply(const MergeBucketCommand& cmd);

    const std::vector<Node>& getNodes() const { return _nodes; }
    Timestamp getMaxTimestamp() const { return _maxTimestamp; }
    const std::vector<uint16_t>& getChain() const { return _chain; }
    uint32_t getClusterStateVersion() const { return _clusterStateVersion; }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    DECLARE_STORAGEREPLY(MergeBucketReply, onMergeBucketReply)
};

/**
 * @class GetBucketDiff
 * @ingroup message
 *
 * @brief Message sent between storage nodes as the first step of merge.
 */
class GetBucketDiffCommand : public BucketCommand {
public:
    typedef MergeBucketCommand::Node Node;

    struct Entry : public document::Printable {
        Timestamp _timestamp;
        document::GlobalId _gid;
        uint32_t _headerSize;
        uint32_t _bodySize;
        uint16_t _flags;
        uint16_t _hasMask;

        Entry();
        void print(std::ostream& out, bool verbose, const std::string& indent) const override;
        bool operator==(const Entry&) const;
        bool operator<(const Entry& e) const
            { return (_timestamp < e._timestamp); }
    };
private:
    std::vector<Node> _nodes;
    Timestamp _maxTimestamp;
    std::vector<Entry> _diff;

public:
    GetBucketDiffCommand(const document::Bucket &bucket,
                         const std::vector<Node>&,
                         Timestamp maxTimestamp);
    ~GetBucketDiffCommand();

    const std::vector<Node>& getNodes() const { return _nodes; }
    Timestamp getMaxTimestamp() const { return _maxTimestamp; }
    const std::vector<Entry>& getDiff() const { return _diff; }
    std::vector<Entry>& getDiff() { return _diff; }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    DECLARE_STORAGECOMMAND(GetBucketDiffCommand, onGetBucketDiff)
};

/**
 * @class GetBucketDiffReply
 * @ingroup message
 *
 * @brief Reply of GetBucketDiffCommand
 */
class GetBucketDiffReply : public BucketReply {
public:
    typedef MergeBucketCommand::Node Node;
    typedef GetBucketDiffCommand::Entry Entry;

private:
    std::vector<Node> _nodes;
    Timestamp _maxTimestamp;
    std::vector<Entry> _diff;

public:
    explicit GetBucketDiffReply(const GetBucketDiffCommand& cmd);
    ~GetBucketDiffReply();

    const std::vector<Node>& getNodes() const { return _nodes; }
    Timestamp getMaxTimestamp() const { return _maxTimestamp; }
    const std::vector<Entry>& getDiff() const { return _diff; }
    std::vector<Entry>& getDiff() { return _diff; }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    DECLARE_STORAGEREPLY(GetBucketDiffReply, onGetBucketDiffReply)
};

/**
 * @class ApplyBucketDiff
 * @ingroup  message
 *
 * @brief Sends a chunk of document entries, which the bucket copies can use
 *        to update themselves.
 */
class ApplyBucketDiffCommand : public BucketInfoCommand {
public:
    typedef MergeBucketCommand::Node Node;
    struct Entry : public document::Printable {
        GetBucketDiffCommand::Entry _entry;
        vespalib::string _docName;
        std::vector<char> _headerBlob;
        std::vector<char> _bodyBlob;
        const document::DocumentTypeRepo *_repo;

        Entry();
        Entry(const GetBucketDiffCommand::Entry&);
        Entry(const Entry &);
        Entry & operator = (const Entry &);
        Entry(Entry &&) = default;
        Entry & operator = (Entry &&) = default;
        ~Entry();

        bool filled() const;
        void print(std::ostream& out, bool verbose, const std::string& indent) const override;
        bool operator==(const Entry&) const;
    };
private:
    std::vector<Node> _nodes;
    std::vector<Entry> _diff;
        // We may send more metadata entries that should fit in one apply bucket
        // diff command, to let node pick which ones it wants to fill in first.
        // Nodes should verify that they don't fill a command up with more than
        // this number of bytes.
    uint32_t _maxBufferSize;

public:
    ApplyBucketDiffCommand(const document::Bucket &bucket,
                           const std::vector<Node>& nodes,
                           uint32_t maxBufferSize);
    ~ApplyBucketDiffCommand();

    const std::vector<Node>& getNodes() const { return _nodes; }
    const std::vector<Entry>& getDiff() const { return _diff; }
    std::vector<Entry>& getDiff() { return _diff; }
    uint32_t getMaxBufferSize() const { return _maxBufferSize; }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    DECLARE_STORAGECOMMAND(ApplyBucketDiffCommand, onApplyBucketDiff)
};

/**
 * @class ApplyBucketDiffReply
 * @ingroup message
 *
 * @brief Reply of ApplyBucketDiffCommand
 */
class ApplyBucketDiffReply : public BucketInfoReply {
public:
    typedef MergeBucketCommand::Node Node;
    typedef ApplyBucketDiffCommand::Entry Entry;

private:
    std::vector<Node> _nodes;
    std::vector<Entry> _diff;
    uint32_t _maxBufferSize;

public:
    explicit ApplyBucketDiffReply(const ApplyBucketDiffCommand& cmd);
    ~ApplyBucketDiffReply();

    const std::vector<Node>& getNodes() const { return _nodes; }
    const std::vector<Entry>& getDiff() const { return _diff; }
    std::vector<Entry>& getDiff() { return _diff; }
    uint32_t getMaxBufferSize() const { return _maxBufferSize; }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    DECLARE_STORAGEREPLY(ApplyBucketDiffReply, onApplyBucketDiffReply)
};

/**
 * @class RequestBucketInfoCommand
 * @ingroup message
 *
 * @brief Command for getting bucket info.
 *
 * Used to get checksums of buckets from a storage node.
 * If list of buckets for which to retrieve info is given. If it is empty,
 * it means all buckets.
 * A system state and a distributor index may be given. If given, only info for
 * the buckets that belong to the given distributor should be returned.
 */
class RequestBucketInfoCommand : public StorageCommand {
    document::BucketSpace _bucketSpace;
    std::vector<document::BucketId> _buckets;
    std::unique_ptr<lib::ClusterState> _state;
    uint16_t _distributor;
    vespalib::string _distributionHash;

public:
    explicit RequestBucketInfoCommand(
            document::BucketSpace bucketSpace,
            const std::vector<document::BucketId>& buckets);
    RequestBucketInfoCommand(document::BucketSpace bucketSpace,
                             uint16_t distributor,
                             const lib::ClusterState& state,
                             const vespalib::stringref & _distributionHash);

    RequestBucketInfoCommand(document::BucketSpace bucketSpace,
                             uint16_t distributor,
                             const lib::ClusterState& state);

    const std::vector<document::BucketId>& getBuckets() const { return _buckets; }

    bool hasSystemState() const { return (_state.get() != 0); }
    uint16_t getDistributor() const { return _distributor; }
    const lib::ClusterState& getSystemState() const { return *_state; }

    const vespalib::string& getDistributionHash() const { return _distributionHash; }
    document::BucketSpace getBucketSpace() const { return _bucketSpace; }
    document::Bucket getBucket() const override;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

    DECLARE_STORAGECOMMAND(RequestBucketInfoCommand, onRequestBucketInfo)
};


/**
 * @class RequestBucketInfoReply
 * @ingroup message
 *
 * @brief Answer of a bucket info command.
 */
class RequestBucketInfoReply : public StorageReply {
public:
    struct Entry {
        document::BucketId _bucketId;
        BucketInfo _info;

        bool operator==(const Entry& e) const { return (_bucketId == e._bucketId && _info == e._info); }
        bool operator!=(const Entry& e) const { return !(*this == e); }
        Entry() : _bucketId(), _info() {}
        Entry(const document::BucketId& id, const BucketInfo& info)
            : _bucketId(id), _info(info) {}
        friend std::ostream& operator<<(std::ostream& os, const Entry&);
    };
    typedef vespalib::Array<Entry> EntryVector;
private:
    EntryVector _buckets;

public:

    explicit RequestBucketInfoReply(const RequestBucketInfoCommand& cmd);
    ~RequestBucketInfoReply();
    const EntryVector & getBucketInfo() const { return _buckets; }
    EntryVector & getBucketInfo() { return _buckets; }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGEREPLY(RequestBucketInfoReply, onRequestBucketInfoReply)
};

/**
 * @class NotifyBucketChangeCommand
 * @ingroup message
 *
 * @brief Command for letting others know a bucket have been altered.
 *
 * When the persistence layer notices a bucket has been corrupted, such that
 * it needs to be repaired, this message will be sent to notify others
 * of change. Others being bucket database on storage node, and possibly
 * distributor.
 */
class NotifyBucketChangeCommand : public BucketCommand {
    BucketInfo _info;
public:
    NotifyBucketChangeCommand(const document::Bucket &bucket,
                              const BucketInfo& bucketInfo);
    const BucketInfo& getBucketInfo() const { return _info; }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGECOMMAND(NotifyBucketChangeCommand, onNotifyBucketChange)
};


/**
 * @class NotifyBucketChangeReply
 * @ingroup message
 *
 * @brief Answer of notify bucket command.
 *
 * Noone will resend these messages, and they're not needed, but all commands
 * need to have a reply.
 */
class NotifyBucketChangeReply : public BucketReply {
public:
    explicit NotifyBucketChangeReply(const NotifyBucketChangeCommand& cmd);
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGEREPLY(NotifyBucketChangeReply, onNotifyBucketChangeReply)
};

/**
 * @class SetBucketStateCommand
 * @ingroup message
 *
 * @brief Sent by distributor to set the ready/active state of a bucket.
 */
class SetBucketStateCommand : public MaintenanceCommand
{
public:
    enum BUCKET_STATE
    {
        INACTIVE,
        ACTIVE
    };
private:
    BUCKET_STATE _state;
public:
    SetBucketStateCommand(const document::Bucket &bucket, BUCKET_STATE state);
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    BUCKET_STATE getState() const { return _state; }
    DECLARE_STORAGECOMMAND(SetBucketStateCommand, onSetBucketState)
private:

    vespalib::string getSummary() const override;
};

/**
 * @class SetBucketStateReply
 * @ingroup message
 *
 * @brief Answer to SetBucketStateCommand.
 */
class SetBucketStateReply : public BucketInfoReply
{
public:
    explicit SetBucketStateReply(const SetBucketStateCommand&);
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    DECLARE_STORAGEREPLY(SetBucketStateReply, onSetBucketStateReply)
};

} // api
} // storage
