// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/vespalib/stllike/hash_set.h>
#include <vespa/storageframework/storageframework.h>
#include <vespa/storageapi/message/internal.h>
#include <vespa/persistence/spi/docentry.h>
#include <vespa/persistence/spi/bucket.h>
#include <vespa/persistence/spi/selection.h>
#include <vespa/persistence/spi/read_consistency.h>

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
                   uint32_t maxByteSize)
        : api::InternalCommand(ID),
          _token(std::move(token)),
          _bucketId(bucketId),
          _iteratorId(iteratorId),
          _maxByteSize(maxByteSize)
    {
        assert(_token.get());
    }

    std::unique_ptr<api::StorageReply> makeReply();

    document::BucketId getBucketId() const { return _bucketId; }
    virtual bool hasSingleBucketId() const { return true; }

    spi::IteratorId getIteratorId() const { return _iteratorId; }
    void setIteratorId(spi::IteratorId iteratorId) { _iteratorId = iteratorId; }

    void setMaxByteSize(uint32_t maxByteSize) { _maxByteSize = maxByteSize; }
    uint32_t getMaxByteSize() const { return _maxByteSize; }


    virtual void print(std::ostream& out, bool verbose, const std::string& indent) const
    {
        out << "GetIterCommand()";

        if (verbose) {
            out << " : ";
            InternalCommand::print(out, true, indent);
        }
    }
private:
    framework::MemoryToken::UP releaseMemoryToken() { return std::move(_token); }
    friend class GetIterReply;
};

class GetIterReply : public api::InternalReply {
private:
    framework::MemoryToken::UP _token;
    document::BucketId _bucketId;
    std::vector<spi::DocEntry::LP> _entries;
    bool _completed;

public:
    typedef std::unique_ptr<GetIterReply> UP;
    typedef std::shared_ptr<GetIterReply> SP;
    static const uint32_t ID = 1002;

    GetIterReply(GetIterCommand& cmd)
        : api::InternalReply(ID, cmd),
          _token(cmd.releaseMemoryToken()),
          _bucketId(cmd.getBucketId()),
          _completed(false)
    {
    }

    virtual bool hasSingleBucketId() const { return true; }
    document::BucketId getBucketId() const {
        return _bucketId;
    }

    const std::vector<spi::DocEntry::LP>& getEntries() const {
        return _entries;
    }

    std::vector<spi::DocEntry::LP>& getEntries() {
        return _entries;
    }

    void setCompleted(bool completed = true) { _completed = completed; }
    bool isCompleted() const { return _completed; }

    virtual void print(std::ostream& out, bool verbose, const std::string& indent) const
    {
        out << "GetIterReply()";

        if (verbose) {
            out << " : ";
            InternalReply::print(out, true, indent);
        }
    }
};

inline std::unique_ptr<api::StorageReply> GetIterCommand::makeReply() {
    return std::unique_ptr<api::StorageReply>(new GetIterReply(*this));
}

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
                          spi::IncludedVersions includedVersions)
        : api::InternalCommand(ID),
          _bucketId(bucketId),
          _selection(selection),
          _fieldSet(fields),
          _includedVersions(includedVersions),
          _readConsistency(spi::ReadConsistency::STRONG)
    {
    }

    virtual bool hasSingleBucketId() const { return true; }
    document::BucketId getBucketId() const { return _bucketId; }
    const spi::Selection& getSelection() const { return _selection; }
    spi::IncludedVersions getIncludedVersions() const { return _includedVersions; }
    const std::string& getFields() const { return _fieldSet; }

    void setReadConsistency(spi::ReadConsistency consistency) noexcept {
        _readConsistency = consistency;
    }
    spi::ReadConsistency getReadConsistency() const noexcept {
        return _readConsistency;
    }

    std::unique_ptr<api::StorageReply> makeReply();

    void print(std::ostream& out,
               bool /*verbose*/,
               const std::string& /*indent*/) const
    {
        out << "CreateIteratorCommand(" << _bucketId << ")";
    }
};

class CreateIteratorReply : public api::InternalReply
{
    document::BucketId _bucketId;
    spi::IteratorId _iteratorId;
public:
    static const uint32_t ID = 1004;
    typedef std::unique_ptr<CreateIteratorReply> UP;
    typedef std::shared_ptr<CreateIteratorReply> SP;

    CreateIteratorReply(const CreateIteratorCommand& cmd,
                        spi::IteratorId iteratorId)
        : api::InternalReply(ID, cmd),
          _bucketId(cmd.getBucketId()),
          _iteratorId(iteratorId)
    {
    }

    virtual bool hasSingleBucketId() const { return true; }
    document::BucketId getBucketId() const { return _bucketId; }

    spi::IteratorId getIteratorId() const { return _iteratorId; }

    void print(std::ostream& out,
               bool /*verbose*/,
               const std::string& /*indent*/) const
    {
        out << "CreateIteratorReply(" << _bucketId << ")";
    }
};

inline std::unique_ptr<api::StorageReply>
CreateIteratorCommand::makeReply()
{
    spi::IteratorId id(0);
    return std::unique_ptr<api::StorageReply>(
            new CreateIteratorReply(*this, id));
}

class DestroyIteratorCommand : public api::InternalCommand
{
    spi::IteratorId _iteratorId;
public:
    static const uint32_t ID = 1005;
    typedef std::unique_ptr<DestroyIteratorCommand> UP;
    typedef std::shared_ptr<DestroyIteratorCommand> SP;

    DestroyIteratorCommand(spi::IteratorId iteratorId)
        : api::InternalCommand(ID),
          _iteratorId(iteratorId)
    {
    }

    spi::IteratorId getIteratorId() const { return _iteratorId; }

    std::unique_ptr<api::StorageReply> makeReply();

    void print(std::ostream& out,
               bool /*verbose*/,
               const std::string& /*indent*/) const
    {
        out << "DestroyIteratorCommand(id="
            << _iteratorId
            << ")";
    }
};

class DestroyIteratorReply : public api::InternalReply
{
    spi::IteratorId _iteratorId;
public:
    static const uint32_t ID = 1006;
    typedef std::unique_ptr<DestroyIteratorReply> UP;
    typedef std::shared_ptr<DestroyIteratorReply> SP;

    DestroyIteratorReply(const DestroyIteratorCommand& cmd)
        : api::InternalReply(ID, cmd),
          _iteratorId(cmd.getIteratorId())
    {
    }

    void print(std::ostream& out,
               bool /*verbose*/,
               const std::string& /*indent*/) const
    {
        out << "DestroyIteratorReply(id="
            << _iteratorId
            << ")";
    }
};

inline std::unique_ptr<api::StorageReply>
DestroyIteratorCommand::makeReply() {
    return std::unique_ptr<api::StorageReply>(new DestroyIteratorReply(*this));
}

class RecheckBucketInfoCommand : public api::InternalCommand
{
    document::BucketId _bucketId;
public:
    static const uint32_t ID = 1007;
    typedef std::shared_ptr<RecheckBucketInfoCommand> SP;
    typedef std::unique_ptr<RecheckBucketInfoCommand> UP;

    RecheckBucketInfoCommand(const document::BucketId& bucketId)
        : api::InternalCommand(ID),
          _bucketId(bucketId)
    {}

    document::BucketId getBucketId() const {
        return _bucketId;
    }

    std::unique_ptr<api::StorageReply> makeReply();

    void print(std::ostream& out,
               bool verbose,
               const std::string& indent) const
    {
        (void) verbose;
        (void) indent;
        out << "RecheckBucketInfoCommand("
            << _bucketId
            << ")";
    }
};

class RecheckBucketInfoReply : public api::InternalReply
{
    document::BucketId _bucketId;
public:
    static const uint32_t ID = 1008;
    typedef std::shared_ptr<RecheckBucketInfoReply> SP;
    typedef std::unique_ptr<RecheckBucketInfoReply> UP;

    RecheckBucketInfoReply(const RecheckBucketInfoCommand& cmd)
        : api::InternalReply(ID, cmd),
          _bucketId(cmd.getBucketId())
    {}

    document::BucketId getBucketId() const {
        return _bucketId;
    }

    void print(std::ostream& out,
               bool verbose,
               const std::string& indent) const
    {
        (void) verbose;
        (void) indent;
        out << "RecheckBucketInfoReply("
            << _bucketId
            << ")";
    }
};

inline std::unique_ptr<api::StorageReply>
RecheckBucketInfoCommand::makeReply() {
    return std::unique_ptr<api::StorageReply>(new RecheckBucketInfoReply(*this));
}

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

    typedef vespalib::hash_set<
        document::BucketId,
        document::BucketId::hash
    > BucketSet;

    // Primarily for unit test mocking; actual predicate impl should do lazy
    // evaluations based on previous and current cluster states.
    class ExplicitBucketSetPredicate : public AbortPredicate {
        BucketSet _bucketsToAbort;

        bool doShouldAbort(const document::BucketId& bid) const override {
            return _bucketsToAbort.find(bid) != _bucketsToAbort.end();
        }
    public:
        explicit ExplicitBucketSetPredicate(const BucketSet& bucketsToAbort)
            : _bucketsToAbort(bucketsToAbort)
        {
        }

        template <typename Iterator>
        ExplicitBucketSetPredicate(Iterator first, Iterator last)
            : _bucketsToAbort(first, last)
        {
        }

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
    AbortBucketOperationsCommand(std::unique_ptr<AbortPredicate> predicate)
        : api::InternalCommand(ID),
        _predicate(std::move(predicate))
    {}


    bool shouldAbort(const document::BucketId& bid) const {
        return _predicate->shouldAbort(bid);
    }

    std::unique_ptr<api::StorageReply> makeReply();

    void print(std::ostream& out,
               bool verbose,
               const std::string& indent) const
    {
        (void) verbose;
        (void) indent;
        out << "AbortBucketOperationsCommand()";
    }
};

class AbortBucketOperationsReply : public api::InternalReply
{
public:
    static const uint32_t ID = 1010;
    typedef std::shared_ptr<AbortBucketOperationsReply> SP;
    typedef std::shared_ptr<const AbortBucketOperationsReply> CSP;

    AbortBucketOperationsReply(const AbortBucketOperationsCommand& cmd)
        : api::InternalReply(ID, cmd)
    {}

    void print(std::ostream& out,
               bool verbose,
               const std::string& indent) const
    {
        (void) verbose;
        (void) indent;
        out << "AbortBucketOperationsReply()";
    }
};

inline std::unique_ptr<api::StorageReply>
AbortBucketOperationsCommand::makeReply() {
    return std::unique_ptr<api::StorageReply>(new AbortBucketOperationsReply(*this));
}

} // ns storage

