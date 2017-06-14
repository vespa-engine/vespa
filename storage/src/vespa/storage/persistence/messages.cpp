// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "messages.h"

namespace storage {

GetIterCommand::GetIterCommand(framework::MemoryToken::UP token,
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

GetIterCommand::~GetIterCommand() { }

void
GetIterCommand::print(std::ostream& out, bool verbose, const std::string& indent) const {
    out << "GetIterCommand()";

    if (verbose) {
        out << " : ";
        InternalCommand::print(out, true, indent);
    }
}

std::unique_ptr<api::StorageReply>
GetIterCommand::makeReply() {
    return std::make_unique<GetIterReply>(*this);
}

GetIterReply::GetIterReply(GetIterCommand& cmd)
    : api::InternalReply(ID, cmd),
      _token(cmd.releaseMemoryToken()),
      _bucketId(cmd.getBucketId()),
      _completed(false)
{ }

GetIterReply::~GetIterReply() { }

void
GetIterReply::print(std::ostream& out, bool verbose, const std::string& indent) const {
    out << "GetIterReply()";

    if (verbose) {
        out << " : ";
        InternalReply::print(out, true, indent);
    }
}

CreateIteratorCommand::CreateIteratorCommand(const document::BucketId& bucketId,
                                             const spi::Selection& selection,
                                             const std::string& fields,
                                             spi::IncludedVersions includedVersions)
    : api::InternalCommand(ID),
      _bucketId(bucketId),
      _selection(selection),
      _fieldSet(fields),
      _includedVersions(includedVersions),
      _readConsistency(spi::ReadConsistency::STRONG)
{ }

CreateIteratorCommand::~CreateIteratorCommand() { }

void
CreateIteratorCommand::print(std::ostream& out, bool, const std::string &) const {
    out << "CreateIteratorCommand(" << _bucketId << ")";
}

std::unique_ptr<api::StorageReply>
CreateIteratorCommand::makeReply() {
    spi::IteratorId id(0);
    return std::make_unique<CreateIteratorReply>(*this, id);
}

CreateIteratorReply::CreateIteratorReply(const CreateIteratorCommand& cmd, spi::IteratorId iteratorId)
    : api::InternalReply(ID, cmd),
      _bucketId(cmd.getBucketId()),
      _iteratorId(iteratorId)
{ }

CreateIteratorReply::~CreateIteratorReply() { }

void
CreateIteratorReply::print(std::ostream& out, bool, const std::string &) const {
    out << "CreateIteratorReply(" << _bucketId << ")";
}

DestroyIteratorCommand::DestroyIteratorCommand(spi::IteratorId iteratorId)
    : api::InternalCommand(ID),
      _iteratorId(iteratorId)
{ }

DestroyIteratorCommand::~DestroyIteratorCommand() { }

void
DestroyIteratorCommand::print(std::ostream& out, bool, const std::string &) const {
    out << "DestroyIteratorCommand(id=" << _iteratorId << ")";
}

DestroyIteratorReply::DestroyIteratorReply(const DestroyIteratorCommand& cmd)
    : api::InternalReply(ID, cmd),
      _iteratorId(cmd.getIteratorId())
{ }

DestroyIteratorReply::~DestroyIteratorReply() { }

void
DestroyIteratorReply::print(std::ostream& out, bool, const std::string &) const {
    out << "DestroyIteratorReply(id=" << _iteratorId << ")";
}

std::unique_ptr<api::StorageReply>
DestroyIteratorCommand::makeReply() {
    return std::make_unique<DestroyIteratorReply>(*this);
}

RecheckBucketInfoCommand::RecheckBucketInfoCommand(const document::BucketId& bucketId)
    : api::InternalCommand(ID),
      _bucketId(bucketId)
{ }

RecheckBucketInfoCommand::~RecheckBucketInfoCommand() { }

void
RecheckBucketInfoCommand::print(std::ostream& out, bool, const std::string &) const {
    out << "RecheckBucketInfoCommand(" << _bucketId << ")";
}

RecheckBucketInfoReply::RecheckBucketInfoReply(const RecheckBucketInfoCommand& cmd)
    : api::InternalReply(ID, cmd),
      _bucketId(cmd.getBucketId())
{ }

RecheckBucketInfoReply::~RecheckBucketInfoReply() { }

void
RecheckBucketInfoReply::print(std::ostream& out, bool, const std::string &) const {
    out << "RecheckBucketInfoReply(" << _bucketId << ")";
}

std::unique_ptr<api::StorageReply>
RecheckBucketInfoCommand::makeReply() {
    return std::make_unique<RecheckBucketInfoReply>(*this);
}

bool
AbortBucketOperationsCommand::ExplicitBucketSetPredicate::doShouldAbort(const document::BucketId& bid) const {
    return _bucketsToAbort.find(bid) != _bucketsToAbort.end();
}

AbortBucketOperationsCommand::ExplicitBucketSetPredicate::ExplicitBucketSetPredicate(const BucketSet& bucketsToAbort)
    : _bucketsToAbort(bucketsToAbort)
{ }

AbortBucketOperationsCommand::ExplicitBucketSetPredicate::~ExplicitBucketSetPredicate() { }

AbortBucketOperationsCommand::AbortBucketOperationsCommand(std::unique_ptr<AbortPredicate> predicate)
    : api::InternalCommand(ID),
    _predicate(std::move(predicate))
{ }

AbortBucketOperationsCommand::~AbortBucketOperationsCommand() { }


void
AbortBucketOperationsCommand::print(std::ostream& out, bool, const std::string &) const {
    out << "AbortBucketOperationsCommand()";
}

AbortBucketOperationsReply::AbortBucketOperationsReply(const AbortBucketOperationsCommand& cmd)
    : api::InternalReply(ID, cmd)
{ }

AbortBucketOperationsReply::~AbortBucketOperationsReply() { }

void
AbortBucketOperationsReply::print(std::ostream& out, bool, const std::string &) const {
    out << "AbortBucketOperationsReply()";
}

std::unique_ptr<api::StorageReply>
AbortBucketOperationsCommand::makeReply() {
    return std::make_unique<AbortBucketOperationsReply>(*this);
}

}
