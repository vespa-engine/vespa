// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "result.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <ostream>

namespace storage::spi {

Result::Result(const Result &) = default;
Result & Result::operator = (const Result &) = default;
Result::~Result() = default;

vespalib::string
Result::toString() const {
    vespalib::asciistream os;
    os << "Result(" << static_cast<int>(_errorCode) << ", " << _errorMessage << ")";
    return os.str();
}

std::ostream &
operator << (std::ostream & os, const Result & r) {
    return os << r.toString();
}

std::ostream & operator << (std::ostream & os, const Result::ErrorType &errorCode) {
    return os << static_cast<int>(errorCode);
}

GetResult::GetResult(Document::UP doc, Timestamp timestamp)
    : Result(),
      _timestamp(timestamp),
      _doc(std::move(doc))
{ }

GetResult::~GetResult() = default;
BucketIdListResult::~BucketIdListResult() = default;

IterateResult::~IterateResult() = default;

}

