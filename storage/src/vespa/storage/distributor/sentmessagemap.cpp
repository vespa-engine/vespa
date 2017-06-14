// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sentmessagemap.h"
#include <vespa/storage/distributor/operations/operation.h>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".distributor.callback.map");

namespace storage::distributor {

SentMessageMap::SentMessageMap()
    : _map()
{
}

SentMessageMap::~SentMessageMap()
{
}


std::shared_ptr<Operation>
SentMessageMap::pop()
{
  std::map<api::StorageMessage::Id, std::shared_ptr<Operation> >::iterator found = _map.begin();

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
    std::map<api::StorageMessage::Id, std::shared_ptr<Operation> >::iterator found = _map.find(id);

    if (found != _map.end()) {
        LOG(spam, "Found Id %" PRIu64 " in callback map: %p", id,
            found->second.get());

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
    LOG(spam, "Inserting callback %p for message %" PRIu64 "",
        callback.get(), id);

    _map[id] = callback;
}

std::string
SentMessageMap::toString() const
{
    std::ostringstream ost;
    std::set<std::string> messages;

    for (Map::const_iterator iter = _map.begin();
         iter != _map.end();
         ++iter)
    {
        messages.insert(iter->second.get()->toString());
    }
    for (std::set<std::string>::const_iterator
             it(messages.begin()), e(messages.end());
         it != e; ++it)
    {
        ost << *it << "\n";
    }

    return ost.str();
}

void
SentMessageMap::clear()
{
    _map.clear();
}

}
