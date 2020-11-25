// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sentmessagemap.h"
#include <vespa/storage/distributor/operations/operation.h>
#include <sstream>
#include <set>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.callback.map");

namespace storage::distributor {

SentMessageMap::SentMessageMap()
    : _map()
{
}

SentMessageMap::~SentMessageMap() = default;


std::shared_ptr<Operation>
SentMessageMap::pop()
{
  auto found = _map.begin();

  if (found != _map.end()) {
      std::shared_ptr<Operation> retVal = found->second;
      _map.erase(found);
      return retVal;
  } else {
      return std::shared_ptr<Operation>();
  }
}

std::shared_ptr<Operation>
SentMessageMap::pop(api::StorageMessage::Id id)
{
    auto found = _map.find(id);

    if (found != _map.end()) {
        LOG(spam, "Found Id %" PRIu64 " in callback map: %p", id, found->second.get());

        std::shared_ptr<Operation> retVal = found->second;
        _map.erase(found);
        return retVal;
    } else {
        LOG(spam, "Did not find Id %" PRIu64 " in callback map", id);

        return std::shared_ptr<Operation>();
    }
}

void
SentMessageMap::insert(api::StorageMessage::Id id, const std::shared_ptr<Operation> & callback)
{
    LOG(spam, "Inserting callback %p for message %" PRIu64 "", callback.get(), id);

    _map[id] = callback;
}

std::string
SentMessageMap::toString() const
{
    std::ostringstream ost;
    std::set<std::string> messages;

    for (const auto & entry : _map) {
        messages.insert(entry.second.get()->toString());
    }
    for (const auto & message : messages) {
        ost << message << "\n";
    }

    return ost.str();
}

void
SentMessageMap::clear()
{
    _map.clear();
}

}
