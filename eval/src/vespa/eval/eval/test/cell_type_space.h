// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/cell_type.h>
#include <vector>

namespace vespalib::eval::test {

/**
 * Helper class used to span out the space describing the cell types
 * of different values.
 **/
class CellTypeSpace
{
private:
    std::vector<CellType> _types;
    std::vector<size_t> _state;
    bool _drop_same;
    bool _drop_different;
    bool _done;

    void step_state(); // will set _done
    bool should_skip(); // will check _done
    void skip_unwanted() {
        while (should_skip()) {
            step_state();
        }
    }

public:
    CellTypeSpace(const std::vector<CellType> &types, size_t n)
        : _types(types), _state(n, 0), _drop_same(false), _drop_different(false), _done(false)
    {
        assert(!types.empty());
        assert(n > 0);
        skip_unwanted();
    }
    CellTypeSpace(const CellTypeSpace& rhs) = default;
    CellTypeSpace(CellTypeSpace&& rhs) noexcept = default;
    ~CellTypeSpace();
    CellTypeSpace &same() {
        _drop_different = true;
        assert(!_drop_same);
        skip_unwanted();
        return *this;
    }
    CellTypeSpace &different() {
        _drop_same = true;
        assert(!_drop_different);
        skip_unwanted();
        return *this;
    }
    size_t n() const { return _state.size(); }
    bool valid() const { return !_done; }
    void next() {
        assert(valid());
        step_state();
        skip_unwanted();
    }
    std::vector<CellType> get() const {
        assert(valid());
        std::vector<CellType> ret;
        for (size_t idx: _state) {
            ret.push_back(_types[idx]);
        }
        return ret;
    }
};

} // namespace
