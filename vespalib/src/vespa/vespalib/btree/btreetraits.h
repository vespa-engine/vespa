// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>

namespace vespalib::btree {

template <size_t LS, size_t IS, size_t PS, bool BS>
struct BTreeTraits {
    static constexpr size_t LEAF_SLOTS = LS;
    static constexpr size_t INTERNAL_SLOTS = IS;
    static constexpr size_t PATH_SIZE = PS;
    static constexpr bool BINARY_SEEK = BS;
};

using BTreeDefaultTraits = BTreeTraits<16, 16, 10, true>;

}
