// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "result.h"
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/stllike/asciistream.h>

namespace storage {

namespace spi {

Result::~Result() { }

vespalib::string
Result::toString() const {
    vespalib::asciistream os;
    os << "Result(" << _errorCode << ", " << _errorMessage << ")";
    return os.str();
}

std::ostream &
operator << (std::ostream & os, const Result & r) {
    return os << r.toString();
}

GetResult::GetResult(Document::UP doc, Timestamp timestamp)
    : Result(),
      _timestamp(timestamp),
      _doc(std::move(doc))
{ }

GetResult::~GetResult() { }
BucketIdListResult::~BucketIdListResult() { }

IterateResult::~IterateResult() { }

}  // namespace spi
}  // namespace storage

