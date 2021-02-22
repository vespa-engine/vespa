// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace vespalib {
    class Slime;
}

namespace config {

/**
 * Simple data container for slime object.
 */
class ConfigDataBuffer
{
public:
    ConfigDataBuffer(const ConfigDataBuffer &) = delete;
    ConfigDataBuffer & operator = (const ConfigDataBuffer &) = delete;
    ConfigDataBuffer(ConfigDataBuffer &&) = default;
    ConfigDataBuffer & operator = (ConfigDataBuffer &&) = default;
    ConfigDataBuffer();
    ~ConfigDataBuffer();
    vespalib::Slime & slimeObject() { return *_slime; }
    const vespalib::Slime & slimeObject() const { return *_slime; }
    const vespalib::string & getEncodedString() const { return _encoded; }
    void setEncodedString(const vespalib::string & encoded) { _encoded = encoded; }
private:
    std::unique_ptr<vespalib::Slime> _slime;
    vespalib::string _encoded;
};

} // namespace config

