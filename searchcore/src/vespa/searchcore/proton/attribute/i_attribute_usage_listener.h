// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton {

/*
 * Interface class for listening to attribute usage changes.
 */
class IAttributeUsageListener
{
public:
    virtual ~IAttributeUsageListener() = default;
    virtual void notify_attribute_usage(const AttributeUsageStats &attribute_usage) = 0;
};

}
