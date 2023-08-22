// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <map>
#include <vespa/storageapi/messageapi/storagemessage.h>

namespace storage::distributor {

class Operation;

class SentMessageMap {
public:
    using Map = std::map<api::StorageMessage::Id, std::shared_ptr<Operation>>;

    SentMessageMap();
    ~SentMessageMap();

    [[nodiscard]] std::shared_ptr<Operation> pop(api::StorageMessage::Id id);
    [[nodiscard]] std::shared_ptr<Operation> pop();

    void insert(api::StorageMessage::Id id, const std::shared_ptr<Operation> & msg);
    void clear();
    [[nodiscard]] uint32_t size() const { return _map.size(); }
    [[nodiscard]] bool empty() const noexcept { return _map.empty(); }
    std::string toString() const;

    Map::const_iterator begin() const noexcept { return _map.cbegin(); }
    Map::const_iterator end() const noexcept { return _map.cend(); }
private:
    Map _map;
};

}
