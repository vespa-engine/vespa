// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_initialization_status_wrapper.h"

#include <vespa/vespalib/data/slime/inserter.h>
#include <vespa/vespalib/data/slime/cursor.h>
#include <chrono>

namespace proton {

namespace {
    std::string timepointToString(search::attribute::InitializationStatus::time_point tp) {
       time_t secs = std::chrono::duration_cast<std::chrono::seconds>(tp.time_since_epoch()).count();
       uint32_t usecs_part = std::chrono::duration_cast<std::chrono::microseconds>(tp.time_since_epoch()).count() % 1000000;
       return std::format("{}.{:06}", secs, usecs_part);
    }
}

AttributeInitializationStatusWrapper::AttributeInitializationStatusWrapper(const std::string &name) :
    _name(name)
    {
}

void AttributeInitializationStatusWrapper::setAttributeVector(const search::AttributeVector::SP &attr) {
    std::unique_lock<std::shared_mutex> guard(_mutex);
    _attr = attr;
}

bool AttributeInitializationStatusWrapper::hasAttributeVector() const {
    std::unique_lock<std::shared_mutex> guard(_mutex);
    return _attr != nullptr;
}

void AttributeInitializationStatusWrapper::reportProgress(const vespalib::slime::Inserter &inserter) const {
    std::shared_lock<std::shared_mutex> guard(_mutex);
    vespalib::slime::Cursor &cursor = inserter.insertObject();
    cursor.setString("name", _name);

    if (!_attr) {
        cursor.setString("status", "queued");
    } else {
        const search::InitializationStatus &status = _attr->getInitializationStatus();

        cursor.setString("status", search::InitializationStatus::stateToString(status.getState()));

        if (status.getState() != search::attribute::InitializationStatus::State::QUEUED) {
            cursor.setString("loading_started", timepointToString(status.getStartTime()));

            if (status.getReprocessingStartTime() >= status.getStartTime()) {
                cursor.setString("reprocessing_started",timepointToString(status.getReprocessingStartTime()));
            }

            if (status.getState() == search::attribute::InitializationStatus::State::REPROCESSING) {
                cursor.setDouble("reprocessing_progress",  status.getReprocessingPercentage());
            }

            if (status.getReprocessingPercentage() > 0.0f &&
                    status.getReprocessingEndTime() >= status.getReprocessingStartTime()) {
                cursor.setString("reprocessing_finished", timepointToString(status.getReprocessingEndTime()));
                    }

            if (status.getEndTime() >= status.getStartTime()) {
                cursor.setString("loading_finished", timepointToString(status.getEndTime()));
            }
        }
    }
}

}
