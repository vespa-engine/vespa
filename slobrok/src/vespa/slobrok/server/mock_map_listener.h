// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "map_listener.h"

namespace slobrok {

enum class MockEvent { NONE, ADD, REMOVE, UPDATE };

struct MockMapListener : public MapListener {
    MockMapListener();
    virtual ~MockMapListener();
    void add(const ServiceMapping &mapping) override;
    void remove(const ServiceMapping &mapping) override;
    void update(const ServiceMapping &old_mapping,
                const ServiceMapping &new_mapping) override;

    MockEvent last_event = MockEvent::NONE;
    ServiceMapping last_add = {{}, {}};
    ServiceMapping last_remove = {{}, {}};

    void clear() { last_event = MockEvent::NONE; }
};

}
