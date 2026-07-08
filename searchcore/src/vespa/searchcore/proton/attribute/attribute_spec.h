// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <string>

namespace search::attribute {
class Config;
}

namespace proton {

/**
 * A specification of an attribute vector an attribute manager should
 * instantiate and manage.
 */
class AttributeSpec {
private:
    using Config = search::attribute::Config;
    std::string             _name;
    std::unique_ptr<Config> _cfg;

public:
    AttributeSpec(const std::string& name, const search::attribute::Config& cfg);
    AttributeSpec(AttributeSpec&&) noexcept;
    AttributeSpec& operator=(AttributeSpec&&) noexcept;
    ~AttributeSpec();
    [[nodiscard]] const std::string& getName() const noexcept { return _name; }
    [[nodiscard]] const Config& getConfig() const noexcept { return *_cfg; }
};

} // namespace proton
