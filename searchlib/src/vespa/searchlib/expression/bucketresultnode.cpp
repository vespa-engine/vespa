// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "bucketresultnode.h"

namespace search {
namespace expression {

IMPLEMENT_IDENTIFIABLE_ABSTRACT_NS2(search, expression, BucketResultNode, vespalib::Identifiable);

vespalib::FieldBase BucketResultNode::_toField("to");
vespalib::FieldBase BucketResultNode::_fromField("from");

}
}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_expression_bucketresultnode() {}
