// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <sys/types.h>

namespace search
{

namespace btree
{

template <size_t LS, size_t IS, size_t PS, bool BS>
struct BTreeTraits {
    static const size_t LEAF_SLOTS = LS;
    static const size_t INTERNAL_SLOTS = IS;
    static const size_t PATH_SIZE = PS;
    static const bool BINARY_SEEK = BS;
};

typedef BTreeTraits<16, 16, 10, true> BTreeDefaultTraits;

} // namespace search::btree
} // namespace search

