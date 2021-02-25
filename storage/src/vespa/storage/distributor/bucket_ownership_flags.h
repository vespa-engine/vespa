// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>

namespace storage::distributor {

/*
 * Compact bucket ownership representation. Default value is not owned
 * by current state or pending state.  Bucket is always considered
 * owned in pending state if there is no pending state.
 */
class BucketOwnershipFlags {
    uint8_t _flags;
    
    static constexpr uint8_t owned_in_current_state_flag = 0x1;
    static constexpr uint8_t owned_in_pending_state_flag = 0x2;
    
public:
    BucketOwnershipFlags() noexcept
        : _flags(0)
    { }

    bool owned_in_current_state() const noexcept { return ((_flags & owned_in_current_state_flag) != 0); }
    bool owned_in_pending_state() const noexcept {  return ((_flags & owned_in_pending_state_flag) != 0); }
    void set_owned_in_current_state() noexcept { _flags |= owned_in_current_state_flag; }
    void set_owned_in_pending_state() noexcept { _flags |= owned_in_pending_state_flag; }
};

}
