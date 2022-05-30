// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "state_explorer.h"
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vector>
#include <memory>

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
    virtual void get_state(const slime::Inserter &inserter, bool full) const override;
    virtual std::vector<vespalib::string> get_children_names() const override;
    virtual std::unique_ptr<StateExplorer> get_child(vespalib::stringref name) const override;
};

} // namespace vespalib
