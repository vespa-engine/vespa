// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib {

/*
 * Interface class describing some hardware on the machine.
 */
class IHwInfo
{
public:
    virtual ~IHwInfo() = default;
    virtual bool spinningDisk() const = 0;
};

}
