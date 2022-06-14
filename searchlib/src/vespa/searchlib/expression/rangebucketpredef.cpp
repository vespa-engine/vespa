// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "rangebucketpredef.h"

namespace search {
namespace expression {

IMPLEMENT_EXPRESSIONNODE(RangeBucketPreDefFunctionNode, UnaryFunctionNode);

RangeBucketPreDefFunctionNode::~RangeBucketPreDefFunctionNode() {}

RangeBucketPreDefFunctionNode::RangeBucketPreDefFunctionNode(const RangeBucketPreDefFunctionNode & rhs) :
    UnaryFunctionNode(rhs),
    _predef(rhs._predef),
    _result(NULL),
    _nullResult(rhs._nullResult),
    _handler()
{
}

RangeBucketPreDefFunctionNode & RangeBucketPreDefFunctionNode::operator = (const RangeBucketPreDefFunctionNode & rhs)
{
    if (this != & rhs) {
        UnaryFunctionNode::operator = (rhs);
        _predef = rhs._predef;
        _result = NULL;
        _nullResult = rhs._nullResult;
        _handler.reset();
    }
    return *this;
}

void
RangeBucketPreDefFunctionNode::onPrepareResult()
{
    // Use the type of the predefined buckets for the null bucket
    const ResultNode& resultNode = _predef->empty() ? *getArg().getResult() : _predef->get(0);
    _nullResult = &resultNode.getNullBucket();

    const vespalib::Identifiable::RuntimeClass & cInfo(getArg().getResult()->getClass());
    if (cInfo.inherits(ResultNodeVector::classId)) {
        setResultType(ResultNode::UP(_predef->clone()));
        static_cast<ResultNodeVector &>(updateResult()).clear();
        _handler.reset(new MultiValueHandler(*this));
        _result = & updateResult();
    } else {
        _result = _predef->empty() ? _nullResult : &_predef->get(0);
        _handler.reset(new SingleValueHandler(*this));
    }
}

bool
RangeBucketPreDefFunctionNode::onExecute() const
{
    getArg().execute();
    const ResultNode * result = _handler->handle(*getArg().getResult());
    _result = result ? result : _nullResult;
    return true;
}

const ResultNode * RangeBucketPreDefFunctionNode::SingleValueHandler::handle(const ResultNode & arg)
{
    return _predef.find(arg);
}

const ResultNode * RangeBucketPreDefFunctionNode::MultiValueHandler::handle(const ResultNode & arg)
{
    const ResultNodeVector & v = static_cast<const ResultNodeVector &>(arg);
    _result.clear();
    for(size_t i(0), m(v.size()); i < m; i++) {
        const ResultNode * bucket = _predef.find(v.get(i));
        if (bucket != NULL) {
            _result.push_back(*bucket);
        } else {
            _result.push_back(*_nullResult);
        }
    }
    return &_result;
}

vespalib::Serializer &
RangeBucketPreDefFunctionNode::onSerialize(vespalib::Serializer &os) const
{
    UnaryFunctionNode::onSerialize(os);
    return os << _predef;
}

vespalib::Deserializer &
RangeBucketPreDefFunctionNode::onDeserialize(vespalib::Deserializer &is)
{
    UnaryFunctionNode::onDeserialize(is);
    return is >> _predef;
}

void
RangeBucketPreDefFunctionNode::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    UnaryFunctionNode::visitMembers(visitor);
    visit(visitor, "predefined", _predef);
}

}
}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_expression_rangebucketpredef() {}
