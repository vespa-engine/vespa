// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/serialnum.h>

namespace proton {

/**
 * A simple interface for increasing a serial number and getting the new
 * serial number.
 **/
class IIncSerialNum {
public:
    virtual ~IIncSerialNum() = default;
    virtual search::SerialNum inc_serial_num() = 0;
};

}
