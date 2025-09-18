// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_db_initialization_status.h"

#include "ddbstate.h"
#include "i_replay_progress_producer.h"
#include <vespa/searchcommon/attribute/attribute_initialization_status.h>

namespace proton {

DocumentDBInitializationStatus::DocumentDBInitializationStatus(const std::string& name, const DDBState& state, const IReplayProgressProducer& replay_progress_producer)
    : _name(name),
      _state(state),
      _replay_progress_producer(replay_progress_producer) {
}

void DocumentDBInitializationStatus::set_attribute_initialization_statuses(std::vector<std::shared_ptr<AttributeInitializationStatus>>&& attribute_initialization_statuses) {
    std::lock_guard<std::mutex> guard(_mutex);

    _attribute_initialization_statuses = std::move(attribute_initialization_statuses);
}

}
