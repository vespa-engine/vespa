// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// @author Vegard Sjonfjell
#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace documentapi {

class TestAndSetCondition {
private:
    vespalib::string _selection;

public:
    TestAndSetCondition()
        : _selection()
    {}
    
    explicit TestAndSetCondition(vespalib::stringref selection)
        : _selection(selection)
    {}

    TestAndSetCondition(const TestAndSetCondition &) = default;
    TestAndSetCondition & operator=(const TestAndSetCondition &) = default;

    TestAndSetCondition(TestAndSetCondition &&) = default;
    TestAndSetCondition & operator=(TestAndSetCondition &&) = default;

    const vespalib::string & getSelection() const { return _selection; }
    bool isPresent() const noexcept { return !_selection.empty(); }
};

}
