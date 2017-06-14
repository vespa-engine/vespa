// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "persistence.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <ostream>

namespace storage {
namespace api {

IMPLEMENT_COMMAND(PutCommand, PutReply)
IMPLEMENT_REPLY(PutReply)
IMPLEMENT_COMMAND(UpdateCommand, UpdateReply)
IMPLEMENT_REPLY(UpdateReply)
IMPLEMENT_COMMAND(GetCommand, GetReply)
IMPLEMENT_REPLY(GetReply)
IMPLEMENT_COMMAND(RemoveCommand, RemoveReply)
IMPLEMENT_REPLY(RemoveReply)
IMPLEMENT_COMMAND(RevertCommand, RevertReply)
IMPLEMENT_REPLY(RevertReply)

TestAndSetCommand::TestAndSetCommand(const MessageType & messageType, const document::BucketId & id)
    : BucketInfoCommand(messageType, id)
{}
TestAndSetCommand::~TestAndSetCommand() { }

PutCommand::PutCommand(const document::BucketId& id,
                       const document::Document::SP& doc, Timestamp time)
    : TestAndSetCommand(MessageType::PUT, id),
      _doc(doc),
      _timestamp(time),
      _updateTimestamp(0)
{
    if (_doc.get() == 0) {
        throw vespalib::IllegalArgumentException(
                "Cannot put a null document", VESPA_STRLOC);
    }
}

PutCommand::~PutCommand() {}

StorageCommand::UP
PutCommand::createCopyToForward(
        const document::BucketId& bucket, uint64_t timestamp) const
{
    StorageCommand::UP cmd(new PutCommand(bucket, _doc, timestamp));
    static_cast<PutCommand&>(*cmd).setUpdateTimestamp(_updateTimestamp);
    return cmd;
}

vespalib::string
PutCommand::getSummary() const
{
    vespalib::asciistream stream;
    stream << "Put(BucketId(0x" << vespalib::hex << getBucketId().getId() << "), "
           << _doc->getId().toString()
           << ", timestamp " << vespalib::dec << _timestamp
           << ')';

    return stream.str();
}

void
PutCommand::print(std::ostream& out, bool verbose,
                  const std::string& indent) const
{
    out << "Put(" << getBucketId() << ", " << _doc->getId()
        << ", timestamp " << _timestamp << ", size "
        << _doc->serialize()->getLength() << ")";
    if (verbose) {
        out << " {\n" << indent << "  ";
        _doc->print(out, verbose, indent + "  ");
        out << "\n" << indent << "}" << " : ";
        BucketInfoCommand::print(out, verbose, indent);
    }
}

PutReply::PutReply(const PutCommand& cmd, bool wasFoundFlag)
    : BucketInfoReply(cmd),
      _docId(cmd.getDocumentId()),
      _document(cmd.getDocument()),
      _timestamp(cmd.getTimestamp()),
      _updateTimestamp(cmd.getUpdateTimestamp()),
      _wasFound(wasFoundFlag)
{
}

PutReply::~PutReply() {}

void
PutReply::print(std::ostream& out, bool verbose,
                 const std::string& indent) const
{
    out << "PutReply("
        << _docId.toString() << ", " << getBucketId() << ", timestamp "
        << _timestamp;

    if (hasBeenRemapped()) {
        out << " (was remapped)";
    }

    out << ")";
    if (verbose) {
        out << " : ";
        BucketInfoReply::print(out, verbose, indent);
    }
}

UpdateCommand::UpdateCommand(const document::BucketId& id,
                             const document::DocumentUpdate::SP& update,
                             Timestamp time)
    : TestAndSetCommand(MessageType::UPDATE, id),
      _update(update),
      _timestamp(time),
      _oldTimestamp(0)
{
    if (_update.get() == 0) {
        throw vespalib::IllegalArgumentException(
                "Cannot update a null update", VESPA_STRLOC);
    }
}

UpdateCommand::~UpdateCommand() {}

vespalib::string
UpdateCommand::getSummary() const {
    vespalib::asciistream stream;
    stream << "Update(BucketId(0x" << vespalib::hex << getBucketId().getId() << "), "
           << _update->getId().toString()
           << ", timestamp " << vespalib::dec << _timestamp;
    if (_oldTimestamp != 0) {
        stream << ", old timestamp " << _oldTimestamp;
    }
    stream << ')';

    return stream.str();
}

void
UpdateCommand::print(std::ostream& out, bool verbose,
                     const std::string& indent) const
{
    out << "Update(" << getBucketId() << ", " << _update->getId()
        << ", timestamp " << _timestamp;
    if (_oldTimestamp != 0) {
        out << ", old timestamp " << _oldTimestamp;
    }
    out << ")";
    if (verbose) {
        out << " {\n" << indent << "  ";
        _update->print(out, verbose, indent + "  ");
        out << "\n" << indent << "} : ";
        BucketInfoCommand::print(out, verbose, indent);
    }
}

StorageCommand::UP
UpdateCommand::createCopyToForward(
        const document::BucketId& bucket, uint64_t timestamp) const
{
    StorageCommand::UP cmd(new UpdateCommand(bucket, _update, timestamp));
    static_cast<UpdateCommand&>(*cmd).setOldTimestamp(_oldTimestamp);
    return cmd;
}

UpdateReply::UpdateReply(const UpdateCommand& cmd, Timestamp oldTimestamp)
    : BucketInfoReply(cmd),
      _docId(cmd.getDocumentId()),
      _timestamp(cmd.getTimestamp()),
      _oldTimestamp(oldTimestamp),
      _consistentNode((uint16_t)-1)
{
}

UpdateReply::~UpdateReply() {}

void
UpdateReply::print(std::ostream& out, bool verbose,
                 const std::string& indent) const
{
    out << "UpdateReply("
        << _docId.toString() << ", " << getBucketId() << ", timestamp "
        << _timestamp << ", timestamp of updated doc: " << _oldTimestamp;

    if (_consistentNode != (uint16_t)-1) {
        out << " Was inconsistent (best node " << _consistentNode << ")";
    }

    out << ")";

    if (verbose) {
        out << " : ";
        BucketInfoReply::print(out, verbose, indent);
    }
}

GetCommand::GetCommand(const document::BucketId& bid,
                       const document::DocumentId& docId,
                       const vespalib::stringref & fieldSet, Timestamp before)
    : BucketInfoCommand(MessageType::GET, bid),
      _docId(docId),
      _beforeTimestamp(before),
      _fieldSet(fieldSet)
{
}

GetCommand::~GetCommand() {}

StorageCommand::UP
GetCommand::createCopyToForward(
        const document::BucketId& bucket, uint64_t timestamp) const
{
    (void) timestamp;
    StorageCommand::UP cmd(new GetCommand(
            bucket, _docId, _fieldSet, _beforeTimestamp));
    return cmd;
}

vespalib::string
GetCommand::getSummary() const
{
    vespalib::asciistream stream;
    stream << "Get(BucketId(" << vespalib::hex << getBucketId().getId() << "), "
           << _docId.toString()
           << ", beforetimestamp " << vespalib::dec << _beforeTimestamp
           << ')';

    return stream.str();
}


void
GetCommand::print(std::ostream& out, bool verbose,
                     const std::string& indent) const
{
    out << "Get(" << getBucketId() << ", " << _docId << ")";
    if (verbose) {
        out << " : ";
        BucketCommand::print(out, verbose, indent);
    }
}

GetReply::GetReply(const GetCommand& cmd,
                   const document::Document::SP& doc,
                   Timestamp lastModified)
    : BucketInfoReply(cmd),
      _docId(cmd.getDocumentId()),
      _fieldSet(cmd.getFieldSet()),
      _doc(doc),
      _beforeTimestamp(cmd.getBeforeTimestamp()),
      _lastModifiedTime(lastModified)
{
}

GetReply::~GetReply() {}

void
GetReply::print(std::ostream& out, bool verbose,
                 const std::string& indent) const
{
    out << "GetReply(" << getBucketId() << ", " << _docId
        << ", timestamp " << _lastModifiedTime << ")";
    if (verbose) {
        out << " : ";
        BucketReply::print(out, verbose, indent);
    }
}

RemoveCommand::RemoveCommand(const document::BucketId& bid,
                             const document::DocumentId& docId,
                             Timestamp timestamp)
    : TestAndSetCommand(MessageType::REMOVE, bid),
      _docId(docId),
      _timestamp(timestamp)
{
}

RemoveCommand::~RemoveCommand() {}

StorageCommand::UP
RemoveCommand::createCopyToForward(
        const document::BucketId& bucket, uint64_t timestamp) const
{
    StorageCommand::UP cmd(new RemoveCommand(bucket, _docId, timestamp));
    return cmd;
}

vespalib::string
RemoveCommand::getSummary() const {
    vespalib::asciistream stream;
    stream << "Remove(BucketId(0x" << vespalib::hex << getBucketId().getId() << "), "
           << _docId.toString() << ", timestamp " << vespalib::dec << _timestamp << ')';

    return stream.str();
}
void
RemoveCommand::print(std::ostream& out, bool verbose,
                     const std::string& indent) const
{
    out << "Remove(" << getBucketId() << ", " << _docId
        << ", timestamp " << _timestamp << ")";
    if (verbose) {
        out << " : ";
        BucketInfoCommand::print(out, verbose, indent);
    }
}

RemoveReply::RemoveReply(const RemoveCommand& cmd, Timestamp oldTimestamp)
    : BucketInfoReply(cmd),
      _docId(cmd.getDocumentId()),
      _timestamp(cmd.getTimestamp()),
      _oldTimestamp(oldTimestamp)
{
}

RemoveReply::~RemoveReply() {}

void
RemoveReply::print(std::ostream& out, bool verbose,
                 const std::string& indent) const
{
    out << "RemoveReply(" << getBucketId() << ", " << _docId
        << ", timestamp " << _timestamp;
    if (_oldTimestamp != 0) {
        out << ", removed doc from " << _oldTimestamp;
    } else {
        out << ", not found";
    }
    out << ")";
    if (verbose) {
        out << " : ";
        BucketInfoReply::print(out, verbose, indent);
    }
}

RevertCommand::RevertCommand(const document::BucketId& id,
                             const std::vector<Timestamp>& revertTokens)
    : BucketInfoCommand(MessageType::REVERT, id),
      _tokens(revertTokens)
{
}

RevertCommand::~RevertCommand() {}

void
RevertCommand::print(std::ostream& out, bool verbose,
                     const std::string& indent) const
{
    out << "Revert(" << getBucketId();
    if (verbose) {
        out << ",";
        for (uint32_t i=0; i<_tokens.size(); ++i) {
            out << "\n" << indent << "  " << _tokens[i];
        }
    }
    out << ")";
    if (verbose) {
        out << " : ";
        BucketInfoCommand::print(out, verbose, indent);
    }
}

RevertReply::RevertReply(const RevertCommand& cmd)
    : BucketInfoReply(cmd),
      _tokens(cmd.getRevertTokens())
{
}

RevertReply::~RevertReply() {}

void
RevertReply::print(std::ostream& out, bool verbose,
                   const std::string& indent) const
{
    out << "RevertReply(" << getBucketId() << ")";
    if (verbose) {
        out << " : ";
        BucketInfoReply::print(out, verbose, indent);
    }
}

} // api
} // storage
