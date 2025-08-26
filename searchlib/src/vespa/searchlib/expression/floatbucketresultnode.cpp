// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "floatbucketresultnode.h"
#include <vespa/vespalib/objects/visit.h>
#include <cmath>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.expression.floatbucketresultnode");

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
    const auto &other = static_cast<const FloatBucketResultNode &>(b);
    double f1(_from);
    double f2(other._from);

    if (f1 < f2) return -1;
    if (f1 > f2) return 1;

    double t1(_to);
    double t2(other._to);

    if (f1 == f2) [[likely]] {
        if (t1 == t2) [[likely]] return 0;
        if (t1 < t2) return -1;
        if (t1 > t2) return 1;

        // at least one of t1,t2 is NaN; this is bad
        if (std::isnan(t1)) {
            LOG(warning, "Unexpected limits FloatBucketResultNode: [%g,%g>", f1, t1);
            if (std::isnan(t2)) {
                return 0;
            }
            return -1;
        } else if (std::isnan(t2)) {
            LOG(warning, "Unexpected limits FloatBucketResultNode: [%g,%g>", f2, t2);
            return 1;
        }
    }
    // at least one of f1,f2 is NaN
    else if (std::isnan(f1)) {
        if (! std::isnan(t1)) {
            LOG(warning, "Unexpected limits FloatBucketResultNode: [%g,%g>", f1, t1);
        }
        if (std::isnan(f2)) {
            if (! std::isnan(t2)) {
                LOG(warning, "Unexpected limits FloatBucketResultNode: [%g,%g>", f2, t2);
            }
            if (std::isnan(t1) && std::isnan(t2)) [[likely]] {
                return 0;
            }
            if (std::isnan(t1) || t1 < t2) {
                return -1;
            }
            if (std::isnan(t2) || t1 > t2) {
                return 1;
            }
            return 0;
        }
        return -1;
    }
    else if (std::isnan(f2)) {
        if (! std::isnan(t2)) {
            LOG(warning, "Unexpected limits FloatBucketResultNode: [%g,%g>", f2, t2);
        }
        return 1;
    }
    // this should not be possible - consider assert
    LOG(error, "BAD comparisons in FloatBucketResultNode: [%g,%g> cannot be compared with [%g,%g>", f1, t1, f2, t2);
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
