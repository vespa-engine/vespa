// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <atomic>

namespace storage::api { class StorageMessage; }

namespace feedbm {

class StorageReplyErrorChecker {
    std::atomic<uint32_t> _errors;
public:
    StorageReplyErrorChecker();
    ~StorageReplyErrorChecker();
    void check_error(const storage::api::StorageMessage &msg);
    uint32_t get_error_count() const { return _errors; }
};

}
