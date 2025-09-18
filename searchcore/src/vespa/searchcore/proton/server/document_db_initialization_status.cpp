// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_db_initialization_status.h"

#include "ddbstate.h"
#include "i_replay_progress_producer.h"
#include <vespa/searchcommon/attribute/attribute_initialization_status.h>
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/data/slime/inserter.h>
#include <vespa/vespalib/data/slime/slime.h>

namespace proton {

DocumentDBInitializationStatus::DocumentDBInitializationStatus(const std::string& name, const std::shared_ptr<const DDBState>& state)
    : _name(name),
      _state(state) {
}

void DocumentDBInitializationStatus::set_attribute_initialization_statuses(std::vector<std::shared_ptr<AttributeInitializationStatus>>&& attribute_initialization_statuses) {
    std::lock_guard<std::mutex> guard(_mutex);

    _attribute_initialization_statuses = std::move(attribute_initialization_statuses);
}

void DocumentDBInitializationStatus::set_replay_progress_producer(const std::shared_ptr<const IReplayProgressProducer>& replay_progress_producer) {
    std::lock_guard<std::mutex> guard(_mutex);
    _replay_progress_producer = replay_progress_producer;
}

void DocumentDBInitializationStatus::report_initialization_status(const vespalib::slime::Inserter &inserter) const {
    std::lock_guard<std::mutex> guard(_mutex);

    vespalib::slime::Cursor &db_cursor = inserter.insertObject();
    db_cursor.setString("name", _name);

    DDBState::State state = _state->getState();
    std::string state_string = DDBState::getStateString(state);
    // Make stateString lowercase
    std::transform(state_string.begin(), state_string.end(), state_string.begin(),
           [](unsigned char c){ return std::tolower(c); });
    db_cursor.setString("state", state_string);

    if (state >= DDBState::State::LOAD) {
        db_cursor.setString("loading_started", timepoint_to_string(_state->get_load_time()));
    }

    if (state >= DDBState::State::REPLAY_TRANSACTION_LOG) {
        db_cursor.setString("replay_started", timepoint_to_string(_state->get_replay_time()));
    }

    if (state >= DDBState::State::ONLINE) {
        db_cursor.setString("loading_finished", timepoint_to_string(_state->get_online_time()));
    }

    if (state >= DDBState::State::REPLAY_TRANSACTION_LOG) {
        db_cursor.setString("replay_progress", std::format("{:.6f}", _replay_progress_producer ? _replay_progress_producer->getProgress() : 0.0f));
    }

    vespalib::slime::Cursor &subdb_cursor = db_cursor.setObject("ready_subdb");

    vespalib::slime::Cursor &loaded_cursor = subdb_cursor.setArray("loaded_attributes");
    vespalib::slime::ArrayInserter loaded_array_inserter(loaded_cursor);

    vespalib::slime::Cursor &loading_cursor = subdb_cursor.setArray("loading_attributes");
    vespalib::slime::ArrayInserter loading_array_inserter(loading_cursor);

    vespalib::slime::Cursor &queued_cursor = subdb_cursor.setArray("queued_attributes");
    vespalib::slime::ArrayInserter queued_array_inserter(queued_cursor);

    for (const auto &attribute_status: _attribute_initialization_statuses) {

        search::attribute::AttributeInitializationStatus::State attribute_state = attribute_status->get_state();

        if (attribute_state == search::attribute::AttributeInitializationStatus::State::QUEUED) {
            attribute_status->report_initialization_status(queued_array_inserter);

        } else if (attribute_state == search::attribute::AttributeInitializationStatus::State::LOADED) {
            attribute_status->report_initialization_status(loaded_array_inserter);

        } else { // loading or reprocessing
            attribute_status->report_initialization_status(loading_array_inserter);
        }
    }
}

}
