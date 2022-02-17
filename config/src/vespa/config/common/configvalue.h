// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/common/types.h>
#include <memory>
#include <climits>

namespace vespalib::slime { struct Cursor; }
namespace config::protocol { struct Payload; }
namespace config {

typedef std::shared_ptr<const protocol::Payload> PayloadPtr;

/**
 * Internal representation of a config value. DO NOT USE THIS!!!!! Use readers
 * if you want to instantiate config objects directly.
 */
class ConfigValue {
public:
    explicit ConfigValue(StringVector lines);
    ConfigValue(StringVector lines, const vespalib::string & xxhash);
    ConfigValue(PayloadPtr data, const vespalib::string & xxhash);
    ConfigValue();
    ConfigValue(ConfigValue &&) noexcept = default;
    ConfigValue & operator = (ConfigValue &&) noexcept = default;
    ConfigValue(const ConfigValue &);
    ConfigValue & operator = (const ConfigValue &);
    ~ConfigValue();

    int operator==(const ConfigValue & rhs) const;
    int operator!=(const ConfigValue & rhs) const;

    size_t numLines() const { return _lines.size();  }
    const vespalib::string & getLine(int i) const { return _lines.at(i);  }
    const StringVector & getLines() const { return _lines;  }
    StringVector getLegacyFormat() const;
    vespalib::string asJson() const;
    const vespalib::string& getXxhash64() const { return _xxhash64; }

    void serializeV1(::vespalib::slime::Cursor & cursor) const;
    void serializeV2(::vespalib::slime::Cursor & cursor) const;

    template <typename ConfigType>
    std::unique_ptr<ConfigType> newInstance() const;

private:
    PayloadPtr       _payload;
    StringVector     _lines;
    vespalib::string _xxhash64;
};

} //namespace config
