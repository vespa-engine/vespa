// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "compact_tensor_address_builder.h"
#include <algorithm>

namespace vespalib {
namespace tensor {

namespace
{

void
append(std::vector<char> &address, vespalib::stringref str)
{
    const char *cstr = str.c_str();
    address.insert(address.end(), cstr, cstr + str.size() + 1);
}

}

CompactTensorAddressBuilder::CompactTensorAddressBuilder()
    : _address()
{
}


void
CompactTensorAddressBuilder::add(vespalib::stringref dimension,
                                 vespalib::stringref label)
{
    append(_address, dimension);
    append(_address, label);
}


} // namespace vespalib::tensor
} // namespace vespalib
