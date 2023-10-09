// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/serialnum.h>

namespace proton {

/**
 * A simple interface for getting a serial number
 **/
class IGetSerialNum {
public:
    virtual ~IGetSerialNum() { }
    virtual search::SerialNum getSerialNum() const = 0;
};

}
