// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storage_reply_error_checker.h"
#include <vespa/storageapi/messageapi/storagereply.h>

#include <vespa/log/log.h>
LOG_SETUP(".storage_reply_error_checker");

namespace search::bmcluster {

StorageReplyErrorChecker::StorageReplyErrorChecker()
    : _errors(0u)
{
}

StorageReplyErrorChecker::~StorageReplyErrorChecker() = default;

void
StorageReplyErrorChecker::check_error(const storage::api::StorageMessage &msg)
{
    auto reply = dynamic_cast<const storage::api::StorageReply*>(&msg);
    if (reply != nullptr) {
        if (reply->getResult().failed()) {
            if (++_errors <= 10) {
                LOG(info, "reply '%s', return code '%s'", reply->toString().c_str(), reply->getResult().toString().c_str());
            }
        }
    } else {
        ++_errors;
    }
}

}
