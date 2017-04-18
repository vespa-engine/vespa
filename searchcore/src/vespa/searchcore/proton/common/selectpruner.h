// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/select/node.h>
#include <vespa/document/select/valuenode.h>
#include <vespa/document/select/cloningvisitor.h>
#include <vespa/document/select/operator.h>
#include <vespa/document/select/resultset.h>

namespace search { class IAttributeManager; }

namespace proton
{

class SelectPrunerBase
{
protected:
    const vespalib::string &_docType;
    const search::IAttributeManager *_amgr;
    const document::Document &_emptyDoc;
    const document::DocumentTypeRepo &_repo;
    bool _hasFields;

public:
    SelectPrunerBase(const vespalib::string &docType,
                     const search::IAttributeManager *amgr,
                     const document::Document &emptyDoc,
                     const document::DocumentTypeRepo &repo,
                     bool hasFields);

    SelectPrunerBase(const SelectPrunerBase &rhs);
};


class SelectPruner : public document::select::CloningVisitor,
                      public SelectPrunerBase
{
public:
private:
    bool _inverted;
    bool _wantInverted;
    typedef document::select::Node::UP NodeUP;
    typedef document::select::ValueNode::UP ValueNodeUP;
    uint32_t _attrFieldNodes;
public:
    SelectPruner(const vespalib::string &docType,
                 const search::IAttributeManager *amgr,
                 const document::Document &emptyDoc,
                 const document::DocumentTypeRepo &repo,
                 bool hasFields);

    SelectPruner(const SelectPruner *rhs);

    virtual
    ~SelectPruner(void);

    uint32_t
    getFieldNodes(void) const
    {
        return _fieldNodes;
    }

    uint32_t
    getAttrFieldNodes(void) const
    {
        return _attrFieldNodes;
    }

    const document::select::ResultSet &
    getResultSet(void) const
    {
        return _resultSet;
    }

    bool
    isFalse(void) const;

    bool
    isTrue(void) const;

    bool
    isInvalid(void) const;

    bool
    isConst(void) const;

    void
    trace(std::ostream &t);

    void
    process(const document::select::Node &node);
private:
    virtual void
    visitAndBranch(const document::select::And &expr);

    virtual void
    visitComparison(const document::select::Compare &expr);

    virtual void
    visitDocumentType(const document::select::DocType &expr);

    virtual void
    visitNotBranch(const document::select::Not &expr);

    virtual void
    visitOrBranch(const document::select::Or &expr);

    virtual void
    visitArithmeticValueNode(const document::select::ArithmeticValueNode &
                             expr);

    virtual void
    visitFunctionValueNode(const document::select::FunctionValueNode &expr);

    virtual void
    visitFieldValueNode(const document::select::FieldValueNode &expr);

    void
    invertNode(void);

    const document::select::Operator &
    getOperator(const document::select::Operator &op);

    void
    addNodeCount(const SelectPruner &rhs);

    void
    setInvalidVal(void);

    void
    setInvalidConst(void);

    void
    setTernaryConst(bool val);

    void
    resolveTernaryConst(bool wantInverted);

    bool
    isInvalidVal(void) const;

    bool
    isNullVal(void) const;

    void
    swap(SelectPruner &rhs);
};

} // namespace proton

