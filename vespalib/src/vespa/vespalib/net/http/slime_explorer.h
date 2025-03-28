// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "state_explorer.h"
#include <vespa/vespalib/data/slime/slime.h>
#include <memory>
#include <string>
#include <vector>

namespace vespalib {

/**
 * Simple class exposing the contents of a Slime object through the
 * StateExplorer interface (to be used when testing clients of the
 * StateExplorer interface).
 **/
class SlimeExplorer : public StateExplorer
{
private:
    const slime::Inspector &_self;

public:
    SlimeExplorer(const slime::Inspector &self) : _self(self) {}
    void get_state(const slime::Inserter &inserter, bool full) const override;
    std::vector<std::string> get_children_names() const override;
    std::unique_ptr<StateExplorer> get_child(std::string_view name) const override;
};

} // namespace vespalib
