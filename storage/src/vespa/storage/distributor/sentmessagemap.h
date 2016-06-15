// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/sync.h>
#include <map>
#include <vespa/storageapi/messageapi/storagemessage.h>

namespace storage
{

namespace distributor {

class Operation;

class SentMessageMap
{
public:
    SentMessageMap();

    ~SentMessageMap();

    std::shared_ptr<Operation> pop(api::StorageMessage::Id id);

    std::shared_ptr<Operation> pop();

    void insert(api::StorageMessage::Id id, const std::shared_ptr<Operation> & msg);

    void clear();

    uint32_t size() const { return _map.size(); }

    uint32_t empty() const { return _map.empty(); }

    std::string toString() const;

private:
    typedef std::map<api::StorageMessage::Id, std::shared_ptr<Operation> > Map;

    Map _map;
};

}

}

