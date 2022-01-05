// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storageapi/message/internal.h>
#include <vespa/persistence/spi/bucket.h>
#include <vespa/persistence/spi/selection.h>
#include <vespa/persistence/spi/read_consistency.h>
#include <vespa/persistence/spi/bucketexecutor.h>

namespace storage {

namespace spi { class DocEntry; }

class GetIterCommand : public api::InternalCommand {
private:
    document::Bucket _bucket;
    spi::IteratorId _iteratorId;
    uint32_t _maxByteSize;

public:
    static constexpr uint32_t ID = 1001;
    typedef std::unique_ptr<GetIterCommand> UP;
    typedef std::shared_ptr<GetIterCommand> SP;

    GetIterCommand(const document::Bucket &bucket,
                   spi::IteratorId iteratorId,
                   uint32_t maxByteSize);
    ~GetIterCommand() override;

    std::unique_ptr<api::StorageReply> makeReply() override;

    document::Bucket getBucket() const override { return _bucket; }

    spi::IteratorId getIteratorId() const { return _iteratorId; }
    uint32_t getMaxByteSize() const { return _maxByteSize; }

    api::LockingRequirements lockingRequirements() const noexcept override {
        return api::LockingRequirements::Shared;
    }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
private:
    friend class GetIterReply;
};

class GetIterReply : public api::InternalReply {
private:
    using List = std::vector<std::unique_ptr<spi::DocEntry>>;
    document::Bucket _bucket;
    List             _entries;
    bool             _completed;

public:
    typedef std::unique_ptr<GetIterReply> UP;
    typedef std::shared_ptr<GetIterReply> SP;
    static constexpr uint32_t ID = 1002;

    explicit GetIterReply(GetIterCommand& cmd);
    ~GetIterReply() override;

    document::Bucket getBucket() const override { return _bucket; }

    const List & getEntries() const { return _entries; }

    List & getEntries() { return _entries; }

    void setCompleted(bool completed = true) { _completed = completed; }
    bool isCompleted() const { return _completed; }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

class CreateIteratorCommand : public api::InternalCommand
{
    document::Bucket _bucket;
    spi::Selection _selection;
    std::string _fieldSet;
    spi::IncludedVersions _includedVersions;
    spi::ReadConsistency _readConsistency;

public:
    static constexpr uint32_t ID = 1003;
    typedef std::unique_ptr<CreateIteratorCommand> UP;
    typedef std::shared_ptr<CreateIteratorCommand> SP;

    CreateIteratorCommand(const document::Bucket &bucket,
                          const spi::Selection& selection,
                          const std::string& fields,
                          spi::IncludedVersions includedVersions);
    ~CreateIteratorCommand() override;
    document::Bucket getBucket() const override { return _bucket; }
    const spi::Selection& getSelection() const { return _selection; }
    spi::IncludedVersions getIncludedVersions() const { return _includedVersions; }
    const std::string& getFields() const { return _fieldSet; }

    void setReadConsistency(spi::ReadConsistency consistency) noexcept {
        _readConsistency = consistency;
    }
    spi::ReadConsistency getReadConsistency() const noexcept {
        return _readConsistency;
    }
    api::LockingRequirements lockingRequirements() const noexcept override {
        return api::LockingRequirements::Shared;
    }

    std::unique_ptr<api::StorageReply> makeReply() override;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

class CreateIteratorReply : public api::InternalReply
{
    document::Bucket _bucket;
    spi::IteratorId _iteratorId;
public:
    static constexpr uint32_t ID = 1004;
    typedef std::unique_ptr<CreateIteratorReply> UP;
    typedef std::shared_ptr<CreateIteratorReply> SP;

    CreateIteratorReply(const CreateIteratorCommand& cmd, spi::IteratorId iteratorId);
    ~CreateIteratorReply() override;

    document::Bucket getBucket() const override { return _bucket; }

    spi::IteratorId getIteratorId() const { return _iteratorId; }

    void print(std::ostream& out, bool verbose, const std::string & indent) const override;
};

class DestroyIteratorCommand : public api::InternalCommand
{
    spi::IteratorId _iteratorId;
public:
    static constexpr uint32_t ID = 1005;
    typedef std::unique_ptr<DestroyIteratorCommand> UP;
    typedef std::shared_ptr<DestroyIteratorCommand> SP;

    explicit DestroyIteratorCommand(spi::IteratorId iteratorId);
    ~DestroyIteratorCommand() override;

    spi::IteratorId getIteratorId() const { return _iteratorId; }

    std::unique_ptr<api::StorageReply> makeReply() override;

    void print(std::ostream& out, bool, const std::string &) const override;
};

class DestroyIteratorReply : public api::InternalReply
{
    spi::IteratorId _iteratorId;
public:
    static constexpr uint32_t ID = 1006;
    typedef std::unique_ptr<DestroyIteratorReply> UP;
    typedef std::shared_ptr<DestroyIteratorReply> SP;

    explicit DestroyIteratorReply(const DestroyIteratorCommand& cmd);
    ~DestroyIteratorReply() override;

    void print(std::ostream& out, bool verbose, const std::string & indent) const override;
};

class RecheckBucketInfoCommand : public api::InternalCommand
{
    document::Bucket _bucket;
public:
    static constexpr uint32_t ID = 1007;
    typedef std::shared_ptr<RecheckBucketInfoCommand> SP;
    typedef std::unique_ptr<RecheckBucketInfoCommand> UP;

    explicit RecheckBucketInfoCommand(const document::Bucket &bucket);
    ~RecheckBucketInfoCommand() override;

    document::Bucket getBucket() const override { return _bucket; }

    std::unique_ptr<api::StorageReply> makeReply() override;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

class RecheckBucketInfoReply : public api::InternalReply
{
    document::Bucket _bucket;
public:
    static constexpr uint32_t ID = 1008;
    typedef std::shared_ptr<RecheckBucketInfoReply> SP;
    typedef std::unique_ptr<RecheckBucketInfoReply> UP;

    explicit RecheckBucketInfoReply(const RecheckBucketInfoCommand& cmd);
    ~RecheckBucketInfoReply() override;

    document::Bucket getBucket() const override { return _bucket; }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

class AbortBucketOperationsCommand : public api::InternalCommand
{
public:
    class AbortPredicate {
        virtual bool doShouldAbort(const document::Bucket&) const = 0;
    public:
        virtual ~AbortPredicate() {}
        bool shouldAbort(const document::Bucket &bucket) const {
            return doShouldAbort(bucket);
        }
    };

    static constexpr uint32_t ID = 1009;
    typedef std::shared_ptr<AbortBucketOperationsCommand> SP;
    typedef std::shared_ptr<const AbortBucketOperationsCommand> CSP;
private:
    std::unique_ptr<AbortPredicate> _predicate;
public:
    explicit AbortBucketOperationsCommand(std::unique_ptr<AbortPredicate> predicate);
    ~AbortBucketOperationsCommand() override;

    bool shouldAbort(const document::Bucket &bucket) const {
        return _predicate->shouldAbort(bucket);
    }

    std::unique_ptr<api::StorageReply> makeReply() override;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

class AbortBucketOperationsReply : public api::InternalReply
{
public:
    static constexpr uint32_t ID = 1010;
    typedef std::shared_ptr<AbortBucketOperationsReply> SP;
    typedef std::shared_ptr<const AbortBucketOperationsReply> CSP;

    explicit AbortBucketOperationsReply(const AbortBucketOperationsCommand& cmd);
    ~AbortBucketOperationsReply() override;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};


// Internal Command task for bringing along a Bucket and a BucketTask in
// the inner workings of the storagelink chain.
class RunTaskCommand : public api::InternalCommand {
public:
    static constexpr uint32_t ID = 1011;
    RunTaskCommand(const spi::Bucket &bucket, std::unique_ptr<spi::BucketTask> task);
    ~RunTaskCommand();

    document::Bucket getBucket() const override { return _bucket.getBucket(); }
    std::unique_ptr<api::StorageReply> makeReply() override;
    void run(const spi::Bucket & bucket, std::shared_ptr<vespalib::IDestructorCallback> onComplete);

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
    std::unique_ptr<spi::BucketTask> stealTask() { return std::move(_task); }
private:
    std::unique_ptr<spi::BucketTask> _task;
    spi::Bucket                      _bucket;
};

// Simple reply for matching the RunTaskCommand
class RunTaskReply : public api::InternalReply
{
public:
    explicit RunTaskReply(const RunTaskCommand&);
    ~RunTaskReply();
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
private:
    static constexpr uint32_t ID = 1012;
};

} // ns storage

