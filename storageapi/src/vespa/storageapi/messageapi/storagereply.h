// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::api::StorageReply
 * @ingroup messageapi
 *
 * @brief Superclass for all storage replies.
 *
 * A storage reply is a storage message sent in reply to a storage command.
 *
 * @version $Id$
 */

#pragma once

#include "returncode.h"
#include "storagemessage.h"

namespace storage::api {

class StorageCommand;

class StorageReply : public StorageMessage {
    ReturnCode _result;

protected:
    explicit StorageReply(const StorageCommand& cmd,
                          ReturnCode code = ReturnCode(ReturnCode::OK));

public:
    ~StorageReply() override;
    DECLARE_POINTER_TYPEDEFS(StorageReply);

    void setResult(const ReturnCode& r) { _result = r; }
    void setResult(ReturnCode::Result r) { _result = ReturnCode(r); }
    const ReturnCode& getResult() const { return _result; }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

}
