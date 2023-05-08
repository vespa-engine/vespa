// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <map>
#include <vespa/storageapi/messageapi/storagemessage.h>

namespace storage::distributor {

class Operation;

class SentMessageMap {
public:
    SentMessageMap();
    ~SentMessageMap();

    std::shared_ptr<Operation> pop(api::StorageMessage::Id id);
    std::shared_ptr<Operation> pop();

    void insert(api::StorageMessage::Id id, const std::shared_ptr<Operation> & msg);
    void clear();
    uint32_t size() const { return _map.size(); }
    [[nodiscard]] bool empty() const noexcept { return _map.empty(); }
    std::string toString() const;
private:
    using Map = std::map<api::StorageMessage::Id, std::shared_ptr<Operation>>;
    Map _map;
};

}
