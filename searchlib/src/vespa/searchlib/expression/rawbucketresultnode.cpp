// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "rawbucketresultnode.h"
#include <vespa/vespalib/objects/visit.hpp>

namespace search::expression {

IMPLEMENT_RESULTNODE(RawBucketResultNode, BucketResultNode);

RawBucketResultNode RawBucketResultNode::_nullResult;

size_t
RawBucketResultNode::hash() const
{
#if 0
    union {
        uint8_t  cxor[8];
        uint64_t ixor;
    } xorResult;
    xorResult.ixor = 0;
    size_t i(0);
    const size_t m(_from.size());
    const char * c = _from.c_str();
    const uint64_t * ic = reinterpret_cast<const uint64_t *>(c);
    for (; i+8 < m; i+=8) {
        const size_t index(i/8);
        xorResult.ixor ^= ic[index];
    }
    for (; i < m; i++) {
        xorResult.cxor[i%8] ^= c[i];
    }
    return xorResult.ixor;
#else
    return 0;
#endif
}


RawBucketResultNode::RawBucketResultNode()
    : _from(new RawResultNode()),
      _to(new RawResultNode())
{}

RawBucketResultNode::RawBucketResultNode(const RawBucketResultNode&) = default;

RawBucketResultNode::~RawBucketResultNode() = default;

RawBucketResultNode& RawBucketResultNode::operator=(const RawBucketResultNode&) = default;

RawBucketResultNode& RawBucketResultNode::operator=(RawBucketResultNode&&) noexcept = default;

int
RawBucketResultNode::onCmp(const Identifiable & rhs) const
{
    const RawBucketResultNode & b = static_cast<const RawBucketResultNode &>(rhs);
    int diff(_from->cmp(*b._from));
    return (diff == 0) ? _to->cmp(*b._to) : diff;
}

int RawBucketResultNode::contains(const RawBucketResultNode & b) const
{
    int fromDiff(_from->cmp(*b._from));
    int toDiff(_to->cmp(*b._to));
    return (fromDiff < 0) ? std::min(0, toDiff) : std::max(0, toDiff);
}

int RawBucketResultNode::contains(const ConstBufferRef & s) const
{
    RawResultNode v(s.data(), s.size());
    int diff(_from->cmp(v));
    if (diff > 0) {
        return 1;
    } else {
        diff = _to->cmp(v);
        return (diff <= 0) ? -1 : 0;
    }
}

void
RawBucketResultNode::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, _fromField, _from);
    visit(visitor, _toField, _to);
}

vespalib::Serializer &
RawBucketResultNode::onSerialize(vespalib::Serializer & os) const
{
    _from.serialize(os);
    _to.serialize(os);
    return os;
}

vespalib::Deserializer &
RawBucketResultNode::onDeserialize(vespalib::Deserializer & is)
{
    _from.deserialize(is);
    _to.deserialize(is);
    return is;
}

}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_expression_rawbucketresultnode() {}
