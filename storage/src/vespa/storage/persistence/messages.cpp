// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "messages.h"

using document::BucketSpace;

namespace storage {

GetIterCommand::GetIterCommand(const document::Bucket &bucket,
                               const spi::IteratorId iteratorId,
                               uint32_t maxByteSize)
    : api::InternalCommand(ID),
      _bucket(bucket),
      _iteratorId(iteratorId),
      _maxByteSize(maxByteSize)
{
}

GetIterCommand::~GetIterCommand() = default;

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
      _bucket(cmd.getBucket()),
      _completed(false)
{ }

GetIterReply::~GetIterReply() = default;

void
GetIterReply::print(std::ostream& out, bool verbose, const std::string& indent) const {
    out << "GetIterReply()";

    if (verbose) {
        out << " : ";
        InternalReply::print(out, true, indent);
    }
}

CreateIteratorCommand::CreateIteratorCommand(const document::Bucket &bucket,
                                             const spi::Selection& selection,
                                             const std::string& fields,
                                             spi::IncludedVersions includedVersions)
    : api::InternalCommand(ID),
      _bucket(bucket),
      _selection(selection),
      _fieldSet(fields),
      _includedVersions(includedVersions),
      _readConsistency(spi::ReadConsistency::STRONG)
{ }

CreateIteratorCommand::~CreateIteratorCommand() = default;

void
CreateIteratorCommand::print(std::ostream& out, bool, const std::string &) const {
    out << "CreateIteratorCommand(" << _bucket.getBucketId() << ")";
}

std::unique_ptr<api::StorageReply>
CreateIteratorCommand::makeReply() {
    spi::IteratorId id(0);
    return std::make_unique<CreateIteratorReply>(*this, id);
}

CreateIteratorReply::CreateIteratorReply(const CreateIteratorCommand& cmd, spi::IteratorId iteratorId)
    : api::InternalReply(ID, cmd),
      _bucket(cmd.getBucket()),
      _iteratorId(iteratorId)
{ }

CreateIteratorReply::~CreateIteratorReply() = default;

void
CreateIteratorReply::print(std::ostream& out, bool, const std::string &) const {
    out << "CreateIteratorReply(" << _bucket.getBucketId() << ")";
}

DestroyIteratorCommand::DestroyIteratorCommand(spi::IteratorId iteratorId)
    : api::InternalCommand(ID),
      _iteratorId(iteratorId)
{ }

DestroyIteratorCommand::~DestroyIteratorCommand() = default;

void
DestroyIteratorCommand::print(std::ostream& out, bool, const std::string &) const {
    out << "DestroyIteratorCommand(id=" << _iteratorId << ")";
}

DestroyIteratorReply::DestroyIteratorReply(const DestroyIteratorCommand& cmd)
    : api::InternalReply(ID, cmd),
      _iteratorId(cmd.getIteratorId())
{ }

DestroyIteratorReply::~DestroyIteratorReply() = default;

void
DestroyIteratorReply::print(std::ostream& out, bool, const std::string &) const {
    out << "DestroyIteratorReply(id=" << _iteratorId << ")";
}

std::unique_ptr<api::StorageReply>
DestroyIteratorCommand::makeReply() {
    return std::make_unique<DestroyIteratorReply>(*this);
}

RecheckBucketInfoCommand::RecheckBucketInfoCommand(const document::Bucket& bucket)
    : api::InternalCommand(ID),
      _bucket(bucket)
{ }

RecheckBucketInfoCommand::~RecheckBucketInfoCommand() = default;

void
RecheckBucketInfoCommand::print(std::ostream& out, bool, const std::string &) const {
    out << "RecheckBucketInfoCommand(" << _bucket.getBucketId() << ")";
}

RecheckBucketInfoReply::RecheckBucketInfoReply(const RecheckBucketInfoCommand& cmd)
    : api::InternalReply(ID, cmd),
      _bucket(cmd.getBucket())
{ }

RecheckBucketInfoReply::~RecheckBucketInfoReply() = default;

void
RecheckBucketInfoReply::print(std::ostream& out, bool, const std::string &) const {
    out << "RecheckBucketInfoReply(" << _bucket.getBucketId() << ")";
}

std::unique_ptr<api::StorageReply>
RecheckBucketInfoCommand::makeReply() {
    return std::make_unique<RecheckBucketInfoReply>(*this);
}

AbortBucketOperationsCommand::AbortBucketOperationsCommand(std::unique_ptr<AbortPredicate> predicate)
    : api::InternalCommand(ID),
    _predicate(std::move(predicate))
{ }

AbortBucketOperationsCommand::~AbortBucketOperationsCommand() = default;


void
AbortBucketOperationsCommand::print(std::ostream& out, bool, const std::string &) const {
    out << "AbortBucketOperationsCommand()";
}

AbortBucketOperationsReply::AbortBucketOperationsReply(const AbortBucketOperationsCommand& cmd)
    : api::InternalReply(ID, cmd)
{ }

AbortBucketOperationsReply::~AbortBucketOperationsReply() = default;

void
AbortBucketOperationsReply::print(std::ostream& out, bool, const std::string &) const {
    out << "AbortBucketOperationsReply()";
}

std::unique_ptr<api::StorageReply>
AbortBucketOperationsCommand::makeReply() {
    return std::make_unique<AbortBucketOperationsReply>(*this);
}

}
