// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "floatbucketresultnode.h"
#include <vespa/vespalib/objects/visit.h>
#include <cmath>

namespace search::expression {

IMPLEMENT_RESULTNODE(FloatBucketResultNode, BucketResultNode);

FloatBucketResultNode FloatBucketResultNode::_nullResult;

size_t
FloatBucketResultNode::hash() const
{
    size_t tmpHash(0);
    memcpy(&tmpHash, &_from, sizeof(tmpHash));
    return tmpHash;
}

int
FloatBucketResultNode::onCmp(const Identifiable &b) const
{
    double f1(_from);
    double f2(static_cast<const FloatBucketResultNode &>(b)._from);

    if (std::isnan(f1)) {
        return std::isnan(f2) ? 0 : -1;
    } else {
        if (f1 < f2) {
            return -1;
        } else if (f1 > f2) {
            return 1;
        } else {
            double t1(_to);
            double t2(static_cast<const FloatBucketResultNode &>(b)._to);
            if (std::isnan(t2)) {
                return 1;
            } else {
                if (t1 < t2) {
                    return -1;
                } else if (t1 > t2) {
                    return 1;
                }
            }
        }
    }
    return 0;
}

int FloatBucketResultNode::contains(const FloatBucketResultNode & b) const
{
    double diff(_from - b._from);
    if (diff < 0) {
        return (_to < b._to) ? -1 : 0;
    } else {
        return (_to > b._to) ? 1 : 0;
    }
}

void
FloatBucketResultNode::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, _fromField, _from);
    visit(visitor, _toField, _to);
}

vespalib::Serializer &
FloatBucketResultNode::onSerialize(vespalib::Serializer & os) const
{
    return os.put(_from).put(_to);
}

vespalib::Deserializer &
FloatBucketResultNode::onDeserialize(vespalib::Deserializer & is)
{
    return is.get(_from).get(_to);
}

}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_expression_floatbucketresultnode() {}
