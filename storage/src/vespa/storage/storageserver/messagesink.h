// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::MessageSink
 * @ingroup storageserver
 *
 * @brief This class grabs persistence messages, and answers them without doing anything.
 *
 * @version $Id$
 */

#pragma once

#include <vespa/storage/common/storagelink.h>

namespace storage {

class MessageSink : public StorageLink {
public:
    explicit MessageSink();
    MessageSink(const MessageSink &) = delete;
    MessageSink& operator=(const MessageSink &) = delete;
    ~MessageSink();

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;

private:
    DEF_MSG_COMMAND_H(Get);
    DEF_MSG_COMMAND_H(Put);
    DEF_MSG_COMMAND_H(Remove);
    DEF_MSG_COMMAND_H(Revert);
};

}
