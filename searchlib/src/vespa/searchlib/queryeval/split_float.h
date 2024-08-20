// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// $Id$

#pragma once

#include <string>
#include <vector>

namespace search::queryeval {

class SplitFloat
{
private:
    std::vector<std::string> _parts;
public:
    explicit SplitFloat(std::string_view input);
    size_t parts() const { return _parts.size(); }
    const std::string &getPart(size_t i) const { return _parts[i]; }
};

}
