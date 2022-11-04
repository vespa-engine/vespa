// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/typed_cells.h>
#include <vector>

namespace search::tensor {

class SubspaceType;

/*
 * Class containg an empty subspace, used as a bad fallback when we cannot
 * get a real subspace.
 */
class EmptySubspace
{
    std::vector<char>          _empty_space;
    vespalib::eval::TypedCells _cells;
public:
    explicit EmptySubspace(const SubspaceType& type);
    ~EmptySubspace();
    const vespalib::eval::TypedCells& cells() const noexcept { return _cells; }
};

}
