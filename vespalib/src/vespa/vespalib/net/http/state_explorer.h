// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/data/slime/inserter.h>
#include <memory>
#include <string>
#include <vector>

namespace vespalib {

/**
 * Interface used to traverse and expose state of a given component and its children.
 */
struct StateExplorer {
    virtual void get_state(const slime::Inserter &inserter, bool full) const = 0;
    virtual std::vector<std::string> get_children_names() const;
    virtual std::unique_ptr<StateExplorer> get_child(std::string_view name) const;
    virtual ~StateExplorer();
};

} // namespace vespalib
