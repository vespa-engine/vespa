// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "testandsetcondition.h"
#include <sstream>

namespace documentapi {

TestAndSetCondition::TestAndSetCondition(const TestAndSetCondition&) = default;
TestAndSetCondition& TestAndSetCondition::operator=(const TestAndSetCondition&) = default;

TestAndSetCondition::~TestAndSetCondition() = default;

std::string TestAndSetCondition::to_string() const {
    std::ostringstream ss;
    ss << *this;
    return ss.str();
}

std::ostream& operator<<(std::ostream& os, const TestAndSetCondition& cond) {
    os << "TestAndSetCondition(";
    if (cond.has_selection()) {
        os << "selection '" << cond.getSelection() << "'";
    }
    if (cond.has_required_timestamp()) {
        if (cond.has_selection()) {
            os << ", ";
        }
        os << "required_timestamp " << cond.required_timestamp();
    }
    os << ")";
    return os;
}

}
