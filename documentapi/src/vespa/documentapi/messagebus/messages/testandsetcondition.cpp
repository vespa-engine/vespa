// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "testandsetcondition.h"
#include <ostream>

namespace documentapi {

TestAndSetCondition::TestAndSetCondition(const TestAndSetCondition&) = default;
TestAndSetCondition& TestAndSetCondition::operator=(const TestAndSetCondition&) = default;

TestAndSetCondition::~TestAndSetCondition() = default;

std::ostream& operator<<(std::ostream& os, const TestAndSetCondition& cond) {
    os << "TestAndSetCondition(";
    if (cond.has_selection()) {
        os << "selection '" << cond.getSelection() << "'";
    }
    if (cond.has_required_persistence_timestamp()) {
        if (cond.has_selection()) {
            os << ", ";
        }
        os << "required_persistence_timestamp " << cond.required_persistence_timestamp();
    }
    os << ")";
    return os;
}

}