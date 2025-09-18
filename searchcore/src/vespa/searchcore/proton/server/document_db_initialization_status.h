// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <mutex>
#include <string>
#include <vector>

namespace search::attribute { class AttributeInitializationStatus; }

using search::attribute::AttributeInitializationStatus;

namespace proton {

class DDBState;
class IReplayProgressProducer;

/**
 * Class that collects the objects that track the initialization status of a DocumentDB
 * and can report this status into a slime.
 *
 * Thread-safe.
 */
class DocumentDBInitializationStatus {
private:
    const std::string              _name;
    const DDBState&                _state;
    const IReplayProgressProducer& _replay_progress_producer;

    mutable std::mutex _mutex;  // protects vector below
    std::vector<std::shared_ptr<AttributeInitializationStatus>> _attribute_initialization_statuses;

public:
    DocumentDBInitializationStatus(const std::string& name, const DDBState& state, const IReplayProgressProducer& replay_progress_producer);

    void set_attribute_initialization_statuses(std::vector<std::shared_ptr<AttributeInitializationStatus>>&& attribute_initialization_statuses);

    const DDBState& get_state() const { return _state; }
};

}
