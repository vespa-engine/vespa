// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/bitvector.h>

#pragma once

namespace proton::documentmetastore {

struct WhiteListProvider {
    virtual search::BitVector::UP get_white_list_filter() const = 0;
protected:
    ~WhiteListProvider() = default;
};

} // namespace
