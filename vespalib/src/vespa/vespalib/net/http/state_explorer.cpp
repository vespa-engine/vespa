// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "state_explorer.h"

namespace vespalib {

std::vector<std::string>
StateExplorer::get_children_names() const
{
    return std::vector<std::string>();
}
 
std::unique_ptr<StateExplorer>
StateExplorer::get_child(std::string_view) const
{
    return std::unique_ptr<StateExplorer>(nullptr);
}

StateExplorer::~StateExplorer()
{
}

} // namespace vespalib
