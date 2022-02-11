// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <vespa/vespalib/stllike/string.h>

namespace config {

class ConfigDataBuffer;

/**
 * Interface implemented by all generated config objects.
 */
class ConfigInstance {
public:
    typedef std::unique_ptr<ConfigInstance> UP;
    typedef std::shared_ptr<ConfigInstance> SP;
    // Static for this instance's type
    virtual const vespalib::string & defName() const = 0;
    virtual const vespalib::string & defMd5() const = 0;
    virtual const vespalib::string & defNamespace() const = 0;

    virtual void serialize(ConfigDataBuffer & buffer) const = 0;

    virtual ~ConfigInstance() = default;
};

} // namespace config

