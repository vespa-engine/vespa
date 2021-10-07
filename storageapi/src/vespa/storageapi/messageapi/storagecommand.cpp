// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storagecommand.h"
#include <vespa/vespalib/util/exceptions.h>
#include <ostream>

namespace storage::api {

StorageCommand::StorageCommand(const StorageCommand& other)
    : StorageMessage(other, generateMsgId()),
      _timeout(other._timeout),
      _sourceIndex(other._sourceIndex)
{
}

StorageCommand::StorageCommand(const MessageType& type, Priority p)
    : StorageMessage(type, generateMsgId()),
        // Default timeout is unlimited. Set from mbus message. Some internal
        // use want unlimited timeout, (such as readbucketinfo, repair bucket
        // etc)
      _timeout(duration::max()),
      _sourceIndex(0xFFFF)
{
    setPriority(p);
}

StorageCommand::~StorageCommand() = default;

void
StorageCommand::print(std::ostream& out, bool verbose,
                      const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "StorageCommand(" << _type.getName();
    if (_priority != NORMAL) out << ", priority = " << static_cast<int>(_priority);
    if (_sourceIndex != 0xFFFF) out << ", source = " << _sourceIndex;
    out << ", timeout = " << vespalib::count_ms(_timeout) << " ms";
    out << ")";
}

}
