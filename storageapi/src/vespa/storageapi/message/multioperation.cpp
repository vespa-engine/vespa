// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multioperation.h"
#include <vespa/document/update/documentupdate.h>

using document::DocumentTypeRepo;

namespace storage::api {

IMPLEMENT_COMMAND(MultiOperationCommand, MultiOperationReply)
IMPLEMENT_REPLY(MultiOperationReply)

MultiOperationCommand::MultiOperationCommand(const DocumentTypeRepo::SP &repo,
                                             const document::BucketId& id,
                                             int bufferSize,
                                             bool keepTimeStamps_)
    : BucketInfoCommand(MessageType::MULTIOPERATION, id),
      _buffer(),
      _operations(repo, 0, 0),
      _keepTimeStamps(keepTimeStamps_)
{
    _buffer.resize(bufferSize);
    if (_buffer.size() > 0) {
        _operations = vdslib::WritableDocumentList(_operations.getTypeRepo(),
                        &_buffer[0], _buffer.size(), false);
    }
}

MultiOperationCommand::MultiOperationCommand(const DocumentTypeRepo::SP &repo,
                                             const document::BucketId& id,
                                             const std::vector<char>& buffer,
                                             bool keepTimeStamps_)
    : BucketInfoCommand(MessageType::MULTIOPERATION, id),
      _buffer(buffer),
      _operations(repo, 0, 0),
      _keepTimeStamps(keepTimeStamps_)
{
    if (_buffer.size() > 0) {
        _operations = vdslib::WritableDocumentList(_operations.getTypeRepo(),
                        &_buffer[0], _buffer.size(), true);
    }
}

MultiOperationCommand::MultiOperationCommand(const MultiOperationCommand& o)
    : BucketInfoCommand(MessageType::MULTIOPERATION, o.getBucketId()),
      _buffer(o._buffer),
      _operations(o._operations.getTypeRepo(),0, 0),
      _keepTimeStamps(o._keepTimeStamps)
{
    setTimeout(o.getTimeout());
    setSourceIndex(o.getSourceIndex());
    setPriority(o.getPriority());
    if (_buffer.size() > 0) {
        _operations = vdslib::WritableDocumentList(_operations.getTypeRepo(),
                        &_buffer[0], _buffer.size(), true);
    }
}

MultiOperationCommand::~MultiOperationCommand() {}

void
MultiOperationCommand::print(std::ostream& out, bool verbose,
                             const std::string& indent) const
{
    out << "MultiOperationCommand(" << getBucketId()
        << ", size " << _operations.getBufferSize() << ", used space "
        << (_operations.getBufferSize() - _operations.countFree())
        << ", doccount " << _operations.size() << ", keepTimeStamps "
        << _keepTimeStamps << ")";
    if (verbose) {
        out << " {";
        bool first = true;
        for(vdslib::DocumentList::const_iterator it = _operations.begin();
            it != _operations.end(); ++it)
        {
            if (!first) { out << ","; } else { first = false; }
            out << "\n" << indent << "  ";
            if (it->isRemoveEntry()) {
                out << "Remove(" << it->getDocumentId() << ")";
            } else if (it->isUpdateEntry()) {
                out << "Update(" << it->getDocumentId() << ")";
            } else {
                out << "Put(" << it->getDocumentId() << ")";
            }
        }
        out << "\n" << indent << "} : ";
        BucketInfoCommand::print(out, verbose, indent);
    }
}

MultiOperationReply::MultiOperationReply(const MultiOperationCommand& cmd)
    : BucketInfoReply(cmd),
      _highestModificationTimestamp(0)
{
}

void
MultiOperationReply::print(std::ostream& out, bool verbose,
                           const std::string& indent) const
{
    out << "MultiOperationReply(" << getBucketId() << ")";
    if (verbose) {
        out << " : ";
        BucketInfoReply::print(out, verbose, indent);
    }
}

}
