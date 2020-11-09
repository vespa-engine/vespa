// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resultvector.h"

namespace search::expression {

IMPLEMENT_ABSTRACT_EXPRESSIONNODE(ResultNodeVector, ResultNode);
IMPLEMENT_RESULTNODE(BoolResultNodeVector, ResultNodeVector);
IMPLEMENT_RESULTNODE(Int8ResultNodeVector, ResultNodeVector);
IMPLEMENT_RESULTNODE(Int16ResultNodeVector, ResultNodeVector);
IMPLEMENT_RESULTNODE(Int32ResultNodeVector, ResultNodeVector);
IMPLEMENT_RESULTNODE(Int64ResultNodeVector, ResultNodeVector);
IMPLEMENT_RESULTNODE(EnumResultNodeVector, ResultNodeVector);
IMPLEMENT_RESULTNODE(FloatResultNodeVector,   ResultNodeVector);
IMPLEMENT_RESULTNODE(StringResultNodeVector,  ResultNodeVector);
IMPLEMENT_RESULTNODE(RawResultNodeVector,  ResultNodeVector);
IMPLEMENT_RESULTNODE(IntegerBucketResultNodeVector, ResultNodeVector);
IMPLEMENT_RESULTNODE(FloatBucketResultNodeVector,   ResultNodeVector);
IMPLEMENT_RESULTNODE(StringBucketResultNodeVector,  ResultNodeVector);
IMPLEMENT_RESULTNODE(RawBucketResultNodeVector,  ResultNodeVector);
IMPLEMENT_RESULTNODE(GeneralResultNodeVector,  ResultNodeVector);

const ResultNode *
GeneralResultNodeVector::find(const ResultNode & key) const
{
    for (size_t i(0); i < _v.size(); i++) {
        const ResultNode * r = _v[i].get();
        if (r && (key.cmp(*r) == 0)) {
            return _v[i].get();
        }
    }
    return nullptr;
}

size_t
GeneralResultNodeVector::hash() const
{
    size_t h(0);
    for (size_t i(0); i < _v.size(); i++) {
        h ^= _v[i]->hash();
    }
    return h;
}

ResultSerializer &
ResultNodeVector::onSerializeResult(ResultSerializer & os) const
{
    return os.putResult(*this);
}

ResultDeserializer &
ResultNodeVector::onDeserializeResult(ResultDeserializer & is)
{
    return is.getResult(*this);
}

}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_expression_resultvector() {}
