// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// $Id$

#pragma once

#include <vector>
#include <vespa/vespalib/stllike/string.h>

namespace search::queryeval {

class SplitFloat
{
private:
    std::vector<vespalib::string> _parts;
public:
    SplitFloat(const vespalib::string &input);
    size_t parts() const { return _parts.size(); }
    const vespalib::string &getPart(size_t i) const { return _parts[i]; }
};

}

