// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// @author Vegard Sjonfjell
#pragma once

namespace documentapi {

class TestAndSetCondition {
private:
    std::string _selection;

public:
    TestAndSetCondition()
        : _selection()
    {}
    
    TestAndSetCondition(vespalib::stringref selection)
        : _selection(selection)
    {}

    TestAndSetCondition(const TestAndSetCondition &) = default;
    TestAndSetCondition & operator=(const TestAndSetCondition &) = default;

    TestAndSetCondition(TestAndSetCondition &&) = default;
    TestAndSetCondition & operator=(TestAndSetCondition &&) = default;

    const std::string & getSelection() const { return _selection; }
    bool isPresent() const { return !_selection.empty(); }
};

}
