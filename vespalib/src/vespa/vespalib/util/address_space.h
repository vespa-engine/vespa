// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>
#include <iosfwd>

namespace vespalib {

/**
 * Represents an address space with number of bytes/entries used
 * and the limit number of bytes/entries this address space can represent.
 */
class AddressSpace
{
private:
    size_t _used;
    size_t _dead;
    size_t _limit;

public:
    AddressSpace();
    AddressSpace(size_t used_, size_t dead_, size_t limit_);
    size_t used() const { return _used; }
    size_t dead() const { return _dead; }
    size_t limit() const  { return _limit; }
    double usage() const {
        if (_limit > 0) {
            return (double)(_used - _dead) / (double)_limit;
        }
        return 0;
    }
    bool operator==(const AddressSpace &rhs) const {
        return _used == rhs._used && _dead == rhs._dead && _limit == rhs._limit;
    }
};

std::ostream &operator << (std::ostream &out, const AddressSpace &rhs);

}
