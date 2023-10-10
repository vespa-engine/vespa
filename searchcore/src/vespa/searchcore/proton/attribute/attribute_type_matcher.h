// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::attribute { class Config; }

namespace proton
{

/**
 * Class to check if attribute types are compatible or not.
 */
class AttributeTypeMatcher
{
public:
    AttributeTypeMatcher() = default;
    ~AttributeTypeMatcher() = default;
    bool operator()(const search::attribute::Config &oldConfig,
                    const search::attribute::Config &newConfig) const;
};

}
