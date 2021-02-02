// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "alloc_strategy.h"

namespace proton {

enum class SubDbType;

/*
 * Class representing allocation config for proton which can be used
 * to make an allocation strategy for large data structures owned by a 
 * document sub db (e.g. attribute vectors, document meta store).
 */
class AllocConfig
{
    AllocStrategy  _alloc_strategy; // baseline before adjusting for redundancy / searchable copies
    const uint32_t _redundancy;
    const uint32_t _searchable_copies;

public:
    AllocConfig(const AllocStrategy& alloc_strategy, uint32_t redundancy, uint32_t searchable_copies);
    AllocConfig();
    ~AllocConfig();

    bool operator==(const AllocConfig &rhs) const noexcept;
    bool operator!=(const AllocConfig &rhs) const noexcept {
        return !operator==(rhs);
    }
    AllocStrategy make_alloc_strategy(SubDbType sub_db_type) const;
};

}
