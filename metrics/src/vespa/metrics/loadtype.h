// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace metrics {

class LoadType {
public:
    using string = vespalib::string;
    LoadType(uint32_t id, const string& name) : _id(id), _name(name) {}

    uint32_t getId() const { return _id; }
    const string& getName() const { return _name; }

    string toString() const;
private:
    uint32_t _id;
    string _name;
};

typedef std::vector<LoadType> LoadTypeSet;

}
