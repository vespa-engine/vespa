// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/typed_cells.h>
#include <vector>

namespace vespalib::eval { class ValueType; }

namespace search::tensor {

/*
 * Class containg an empty subspace, used as a bad fallback when we cannot
 * get a real subspace.
 */
class EmptySubspace
{
    std::vector<char>          _empty_space;
    vespalib::eval::TypedCells _empty;
public:
    EmptySubspace(const vespalib::eval::ValueType& type);
    ~EmptySubspace();
    const vespalib::eval::TypedCells& empty() const noexcept { return _empty; }
};

}
