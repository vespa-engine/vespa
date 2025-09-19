// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/net/http/initialization_status_producer.h>

#include <memory>
#include <mutex>
#include <string>
#include <vector>

namespace search::attribute { class AttributeInitializationStatus; }
namespace vespalib::slime { struct Inserter; }

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
class DocumentDBInitializationStatus : public vespalib::InitializationStatusProducer {
private:
    const std::string                        _name;
    std::shared_ptr<const DDBState>                _state;
    std::shared_ptr<const IReplayProgressProducer> _replay_progress_producer;

    mutable std::mutex _mutex;  // protects vector below
    std::vector<std::shared_ptr<AttributeInitializationStatus>> _attribute_initialization_statuses;

public:
    DocumentDBInitializationStatus(const std::string& name, const std::shared_ptr<const DDBState>& state);
    ~DocumentDBInitializationStatus() override __attribute__((noinline)) = default; // Avoid warning about inlining

    void set_attribute_initialization_statuses(std::vector<std::shared_ptr<AttributeInitializationStatus>>&& attribute_initialization_statuses);

    void set_replay_progress_producer(const std::shared_ptr<const IReplayProgressProducer> &replay_progress_producer);

    const DDBState& get_state() const { return *_state; }

    void report_initialization_status(const vespalib::slime::Inserter &inserter) const override;
};

}
