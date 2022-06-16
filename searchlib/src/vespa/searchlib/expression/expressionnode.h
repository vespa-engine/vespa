// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/common/identifiable.h>
#include <vespa/vespalib/objects/identifiable.hpp>
#include <vespa/vespalib/objects/visit.h>

namespace search::attribute { class IAttributeContext; }

namespace search::expression {

typedef uint32_t DocId;

class ResultNode;

#define DECLARE_ABSTRACT_EXPRESSIONNODE(Class) DECLARE_IDENTIFIABLE_ABSTRACT_NS2(search, expression, Class)
#define DECLARE_ABSTRACT_EXPRESSIONNODE_NS1(ns, Class) DECLARE_IDENTIFIABLE_ABSTRACT_NS3(search, expression, ns, Class)

#define DECLARE_EXPRESSIONNODE(Class)                   \
    DECLARE_IDENTIFIABLE_NS2(search, expression, Class) \
    Class * clone() const override;

#define DECLARE_EXPRESSIONNODE_NS1(ns, Class)               \
    DECLARE_IDENTIFIABLE_NS3(search, expression, ns, Class) \
    Class * clone() const override;

#define IMPLEMENT_ABSTRACT_EXPRESSIONNODE(Class, base) \
    IMPLEMENT_IDENTIFIABLE_ABSTRACT_NS2(search, expression, Class, base)

#define IMPLEMENT_EXPRESSIONNODE(Class, base) \
    IMPLEMENT_IDENTIFIABLE_NS2(search, expression, Class, base) \
    Class * Class::clone()       const { return new Class(*this); }

class ExpressionNode : public vespalib::Identifiable
{
public:
    DECLARE_ABSTRACT_EXPRESSIONNODE(ExpressionNode);
    using UP = std::unique_ptr<ExpressionNode>;
    using CP = vespalib::IdentifiablePtr<ExpressionNode>;
    virtual const ResultNode * getResult() const = 0;
    bool execute() const { return onExecute(); }
    ExpressionNode & prepare(bool preserveAccurateTypes) { onPrepare(preserveAccurateTypes); return *this; }
    virtual ExpressionNode * clone() const = 0;
    void executeIterative(const ResultNode & arg, ResultNode & result) const;
    virtual void wireAttributes(const search::attribute::IAttributeContext &attrCtx);
protected:
private:
    virtual void onArgument(const ResultNode & arg, ResultNode & result) const;
    virtual void onPrepare(bool preserveAccurateTypes) = 0;
    virtual bool onExecute() const = 0;
};

typedef ExpressionNode::CP * ExpressionNodeArray;

}
