// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/storageframework/generic/memory/memorytoken.h>
#include <vespa/storageapi/message/internal.h>
#include <vespa/persistence/spi/docentry.h>
#include <vespa/persistence/spi/bucket.h>
#include <vespa/persistence/spi/selection.h>
#include <vespa/persistence/spi/read_consistency.h>
#include <vespa/vespalib/stllike/hash_set.h>


namespace storage {

class GetIterCommand : public api::InternalCommand {
private:
    mutable framework::MemoryToken::UP _token;
    document::BucketId _bucketId;
    spi::IteratorId _iteratorId;
    uint32_t _maxByteSize;

public:
    static const uint32_t ID = 1001;
    typedef std::unique_ptr<GetIterCommand> UP;
    typedef std::shared_ptr<GetIterCommand> SP;

    GetIterCommand(framework::MemoryToken::UP token,
                   const document::BucketId& bucketId,
                   const spi::IteratorId iteratorId,
                   uint32_t maxByteSize);
    ~GetIterCommand();

    std::unique_ptr<api::StorageReply> makeReply() override;

    document::BucketId getBucketId() const override { return _bucketId; }
    bool hasSingleBucketId() const override { return true; }

    spi::IteratorId getIteratorId() const { return _iteratorId; }
    void setIteratorId(spi::IteratorId iteratorId) { _iteratorId = iteratorId; }

    void setMaxByteSize(uint32_t maxByteSize) { _maxByteSize = maxByteSize; }
    uint32_t getMaxByteSize() const { return _maxByteSize; }


    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
private:
    framework::MemoryToken::UP releaseMemoryToken() { return std::move(_token); }
    friend class GetIterReply;
};

class GetIterReply : public api::InternalReply {
private:
    framework::MemoryToken::UP _token;
    document::BucketId _bucketId;
    std::vector<spi::DocEntry::UP> _entries;
    bool _completed;

public:
    typedef std::unique_ptr<GetIterReply> UP;
    typedef std::shared_ptr<GetIterReply> SP;
    static const uint32_t ID = 1002;

    GetIterReply(GetIterCommand& cmd);
    ~GetIterReply();

    bool hasSingleBucketId() const override { return true; }
    document::BucketId getBucketId() const override {
        return _bucketId;
    }

    const std::vector<spi::DocEntry::UP>& getEntries() const {
        return _entries;
    }

    std::vector<spi::DocEntry::UP>& getEntries() {
        return _entries;
    }

    void setCompleted(bool completed = true) { _completed = completed; }
    bool isCompleted() const { return _completed; }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

class CreateIteratorCommand : public api::InternalCommand
{
    document::BucketId _bucketId;
    spi::Selection _selection;
    std::string _fieldSet;
    spi::IncludedVersions _includedVersions;
    spi::ReadConsistency _readConsistency;

public:
    static const uint32_t ID = 1003;
    typedef std::unique_ptr<CreateIteratorCommand> UP;
    typedef std::shared_ptr<CreateIteratorCommand> SP;

    CreateIteratorCommand(const document::BucketId& bucketId,
                          const spi::Selection& selection,
                          const std::string& fields,
                          spi::IncludedVersions includedVersions);
    ~CreateIteratorCommand();
    bool hasSingleBucketId() const override { return true; }
    document::BucketId getBucketId() const override { return _bucketId; }
    const spi::Selection& getSelection() const { return _selection; }
    spi::IncludedVersions getIncludedVersions() const { return _includedVersions; }
    const std::string& getFields() const { return _fieldSet; }

    void setReadConsistency(spi::ReadConsistency consistency) noexcept {
        _readConsistency = consistency;
    }
    spi::ReadConsistency getReadConsistency() const noexcept {
        return _readConsistency;
    }

    std::unique_ptr<api::StorageReply> makeReply() override;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

class CreateIteratorReply : public api::InternalReply
{
    document::BucketId _bucketId;
    spi::IteratorId _iteratorId;
public:
    static const uint32_t ID = 1004;
    typedef std::unique_ptr<CreateIteratorReply> UP;
    typedef std::shared_ptr<CreateIteratorReply> SP;

    CreateIteratorReply(const CreateIteratorCommand& cmd, spi::IteratorId iteratorId);
    ~CreateIteratorReply();

    bool hasSingleBucketId() const override { return true; }
    document::BucketId getBucketId() const override { return _bucketId; }

    spi::IteratorId getIteratorId() const { return _iteratorId; }

    void print(std::ostream& out, bool verbose, const std::string & indent) const override;
};

class DestroyIteratorCommand : public api::InternalCommand
{
    spi::IteratorId _iteratorId;
public:
    static const uint32_t ID = 1005;
    typedef std::unique_ptr<DestroyIteratorCommand> UP;
    typedef std::shared_ptr<DestroyIteratorCommand> SP;

    DestroyIteratorCommand(spi::IteratorId iteratorId);
    ~DestroyIteratorCommand();

    spi::IteratorId getIteratorId() const { return _iteratorId; }

    std::unique_ptr<api::StorageReply> makeReply() override;

    void print(std::ostream& out, bool, const std::string &) const override;
};

class DestroyIteratorReply : public api::InternalReply
{
    spi::IteratorId _iteratorId;
public:
    static const uint32_t ID = 1006;
    typedef std::unique_ptr<DestroyIteratorReply> UP;
    typedef std::shared_ptr<DestroyIteratorReply> SP;

    DestroyIteratorReply(const DestroyIteratorCommand& cmd);
    ~DestroyIteratorReply();

    void print(std::ostream& out, bool verbose, const std::string & indent) const override;
};

class RecheckBucketInfoCommand : public api::InternalCommand
{
    document::BucketId _bucketId;
public:
    static const uint32_t ID = 1007;
    typedef std::shared_ptr<RecheckBucketInfoCommand> SP;
    typedef std::unique_ptr<RecheckBucketInfoCommand> UP;

    RecheckBucketInfoCommand(const document::BucketId& bucketId);
    ~RecheckBucketInfoCommand();

    document::BucketId getBucketId() const override {
        return _bucketId;
    }

    std::unique_ptr<api::StorageReply> makeReply() override;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

class RecheckBucketInfoReply : public api::InternalReply
{
    document::BucketId _bucketId;
public:
    static const uint32_t ID = 1008;
    typedef std::shared_ptr<RecheckBucketInfoReply> SP;
    typedef std::unique_ptr<RecheckBucketInfoReply> UP;

    RecheckBucketInfoReply(const RecheckBucketInfoCommand& cmd);
    ~RecheckBucketInfoReply();

    document::BucketId getBucketId() const override {
        return _bucketId;
    }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

class AbortBucketOperationsCommand : public api::InternalCommand
{
public:
    class AbortPredicate {
        virtual bool doShouldAbort(const document::BucketId&) const = 0;
    public:
        virtual ~AbortPredicate() {}
        bool shouldAbort(const document::BucketId& bid) const {
            return doShouldAbort(bid);
        }
    };

    using BucketSet = vespalib::hash_set<document::BucketId, document::BucketId::hash>;

    // Primarily for unit test mocking; actual predicate impl should do lazy
    // evaluations based on previous and current cluster states.
    class ExplicitBucketSetPredicate : public AbortPredicate {
        BucketSet _bucketsToAbort;

        bool doShouldAbort(const document::BucketId& bid) const override;
    public:
        explicit ExplicitBucketSetPredicate(const BucketSet& bucketsToAbort);
        ~ExplicitBucketSetPredicate();

        template <typename Iterator>
        ExplicitBucketSetPredicate(Iterator first, Iterator last)
            : _bucketsToAbort(first, last)
        { }

        const BucketSet& getBucketsToAbort() const {
            return _bucketsToAbort;
        }
    };

    static const uint32_t ID = 1009;
    typedef std::shared_ptr<AbortBucketOperationsCommand> SP;
    typedef std::shared_ptr<const AbortBucketOperationsCommand> CSP;
private:
    std::unique_ptr<AbortPredicate> _predicate;
public:
    AbortBucketOperationsCommand(std::unique_ptr<AbortPredicate> predicate);
    ~AbortBucketOperationsCommand();

    bool shouldAbort(const document::BucketId& bid) const {
        return _predicate->shouldAbort(bid);
    }

    std::unique_ptr<api::StorageReply> makeReply() override;

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

class AbortBucketOperationsReply : public api::InternalReply
{
public:
    static const uint32_t ID = 1010;
    typedef std::shared_ptr<AbortBucketOperationsReply> SP;
    typedef std::shared_ptr<const AbortBucketOperationsReply> CSP;

    AbortBucketOperationsReply(const AbortBucketOperationsCommand& cmd);
    ~AbortBucketOperationsReply();

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

} // ns storage

