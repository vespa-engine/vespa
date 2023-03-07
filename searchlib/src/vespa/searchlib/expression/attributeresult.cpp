// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributeresult.h"
#include <charconv>

namespace search::expression {

IMPLEMENT_RESULTNODE(AttributeResult, ResultNode);
IMPLEMENT_RESULTNODE(IntegerAttributeResult, ResultNode);
IMPLEMENT_RESULTNODE(FloatAttributeResult, ResultNode);

ResultNode::ConstBufferRef
IntegerAttributeResult::onGetString(size_t , BufferRef buf) const {
    if (buf.size() > 1) {
        char * s = buf.str();
        size_t sz = buf.size();
        long v = getAttribute()->getInt(getDocId());
        auto res = std::to_chars(s, s + sz - 1, v, 10);
        if (res.ec == std::errc()) {
            res.ptr[0] = 0;
        } else {
            s[0] = 0;
        }
    }
    return buf;
}

ResultNode::ConstBufferRef
FloatAttributeResult::onGetString(size_t, BufferRef buf) const {
    double val = getAttribute()->getFloat(getDocId());
    int numWritten(std::min(buf.size(), (size_t)std::max(0, snprintf(buf.str(), buf.size(), "%g", val))));
    return ConstBufferRef(buf.str(), numWritten);
}

}
