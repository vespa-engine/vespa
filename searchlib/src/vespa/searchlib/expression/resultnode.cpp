// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resultnode.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/exception.h>
#include <stdexcept>

namespace search {
namespace expression {

int64_t
ResultNode::onGetEnum(size_t index) const {
    (void) index;
    throw vespalib::Exception("search::expression::ResultNode onGetEnum is not implemented");
}

const BucketResultNode&
ResultNode::getNullBucket() const {
    throw std::runtime_error(vespalib::make_string("No null bucket defined for this type"));
}

}
}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_expression_resultnode() {}
