// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/serialnum.h>
#include <memory>
#include <optional>
#include <string>

namespace search { class AttributeVector; }
namespace search::attribute { class Config; }

namespace proton {

/**
 * Interface for a factory for creating and setting up attribute vectors used by
 * an attribute manager.
 */
struct IAttributeFactory
{
    using SP = std::shared_ptr<IAttributeFactory>;
    using AttributeVectorSP = std::shared_ptr<search::AttributeVector>;
    virtual ~IAttributeFactory() = default;
    virtual AttributeVectorSP create(const std::string &name,
                                     const search::attribute::Config &cfg) const = 0;
    virtual void setupEmpty(const AttributeVectorSP &vec,
                            std::optional<search::SerialNum> serialNum) const = 0;
};

} // namespace proton

