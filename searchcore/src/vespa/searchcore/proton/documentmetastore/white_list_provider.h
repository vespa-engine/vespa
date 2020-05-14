// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <memory>

#pragma once

namespace search { class BitVector; }

namespace proton::documentmetastore {

/** Interface for fetching a copy of the white list bitvector */
struct WhiteListProvider {
    virtual std::unique_ptr<search::BitVector> get_white_list_filter() const = 0;
protected:
    ~WhiteListProvider() = default;
};

} // namespace
