// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "mock_map_listener.h"

namespace slobrok {

MockMapListener::MockMapListener() = default;
MockMapListener::~MockMapListener() = default;

void MockMapListener::add(const ServiceMapping &mapping) {
    last_event = MockEvent::ADD;
    last_add = mapping;
}

void MockMapListener::remove(const ServiceMapping &mapping) {
    last_event = MockEvent::REMOVE;
    last_remove = mapping;
}

void MockMapListener::update(const ServiceMapping &old_mapping,
                             const ServiceMapping &new_mapping)
{
    last_event = MockEvent::UPDATE;
    last_remove = old_mapping;
    last_add = new_mapping;
}

}
