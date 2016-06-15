// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/stllike/string.h>

namespace config {

/**
 * Simple data container for slime object.
 */
class ConfigDataBuffer
{
public:
    vespalib::Slime & slimeObject() { return _slime; }
    const vespalib::Slime & slimeObject() const { return _slime; }
    const vespalib::string & getEncodedString() const { return _encoded; }
    void setEncodedString(const vespalib::string & encoded) { _encoded = encoded; }
private:
    vespalib::Slime _slime;
    vespalib::string _encoded;
};

} // namespace config

