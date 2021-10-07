// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/data/slime/inserter.h>
#include <vector>
#include <memory>

namespace vespalib {

/**
 * Interface used to traverse and expose state of a given component and its children.
 */
struct StateExplorer {
    virtual void get_state(const slime::Inserter &inserter, bool full) const = 0;
    virtual std::vector<vespalib::string> get_children_names() const;
    virtual std::unique_ptr<StateExplorer> get_child(vespalib::stringref name) const;
    virtual ~StateExplorer();
};

} // namespace vespalib
