// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/frt/protocol.h>
#include <vespa/config/configgen/configpayload.h>
#include <vespa/vespalib/stllike/string.h>
#include <vector>
#include <memory>
#include <climits>

namespace vespalib::slime { struct Cursor; }

namespace config {

typedef std::shared_ptr<const protocol::Payload> PayloadPtr;

/**
 * Internal representation of a config value. DO NOT USE THIS!!!!! Use readers
 * if you want to instantiate config objects directly.
 */
class ConfigValue {
public:
    typedef std::unique_ptr<ConfigValue> UP;
    ConfigValue(const std::vector<vespalib::string> & lines, const vespalib::string & xxhash);
    ConfigValue(PayloadPtr data, const vespalib::string & xxhash);
    ConfigValue();
    ConfigValue(const ConfigValue &);
    ConfigValue & operator = (const ConfigValue &);
    ~ConfigValue();

    int operator==(const ConfigValue & rhs) const;
    int operator!=(const ConfigValue & rhs) const;

    size_t numLines() const { return _lines.size();  }
    const vespalib::string & getLine(int i) const { return _lines.at(i);  }
    const std::vector<vespalib::string> & getLines() const { return _lines;  }
    std::vector<vespalib::string> getLegacyFormat() const;
    const vespalib::string asJson() const;
    const vespalib::string getXxhash64() const { return _xxhash64; }

    void serializeV1(::vespalib::slime::Cursor & cursor) const;
    void serializeV2(::vespalib::slime::Cursor & cursor) const;

    template <typename ConfigType>
    std::unique_ptr<ConfigType> newInstance() const;

private:
    PayloadPtr _payload;
    std::vector<vespalib::string> _lines;
    vespalib::string _xxhash64;
};

} //namespace config

#include "configvalue.hpp"

