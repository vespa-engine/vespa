// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace search::attribute { class Config; }

namespace proton {

/**
 * A specification of an attribute vector an attribute manager should
 * instantiate and manage.
 */
class AttributeSpec
{
private:
    using Config = search::attribute::Config;
    vespalib::string _name;
    std::unique_ptr<Config> _cfg;
public:
    AttributeSpec(const vespalib::string &name, const search::attribute::Config &cfg);
    AttributeSpec(AttributeSpec &&) noexcept;
    AttributeSpec & operator=(AttributeSpec &&) noexcept;
    ~AttributeSpec();
    const vespalib::string &getName() const { return _name; }
    const Config &getConfig() const { return *_cfg; }
    bool operator==(const AttributeSpec &rhs) const;
};

} // namespace proton

