// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/select/node.h>
#include <vespa/document/select/valuenode.h>
#include <vespa/document/select/cloningvisitor.h>
#include <vespa/document/select/operator.h>
#include <vespa/document/select/resultset.h>

namespace search { class IAttributeManager; }

namespace document { class DocumentTypeRepo; }

namespace proton {

class SelectPrunerBase
{
protected:
    const vespalib::string &_docType;
    const search::IAttributeManager *_amgr;
    const document::Document &_emptyDoc;
    const document::DocumentTypeRepo &_repo;
    bool _hasFields;
    bool _hasDocuments;

public:
    SelectPrunerBase(const vespalib::string &docType,
                     const search::IAttributeManager *amgr,
                     const document::Document &emptyDoc,
                     const document::DocumentTypeRepo &repo,
                     bool hasFields,
                     bool hasDocuments);

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
                 bool hasFields,
                 bool hasDocuments);

    SelectPruner(const SelectPruner *rhs);
    virtual ~SelectPruner();

    uint32_t getFieldNodes() const { return _fieldNodes; }
    uint32_t getAttrFieldNodes() const { return _attrFieldNodes; }
    const document::select::ResultSet & getResultSet() const { return _resultSet; }
    bool isFalse() const;
    bool isTrue() const;
    bool isInvalid() const;
    bool isConst() const;
    void trace(std::ostream &t);
    void process(const document::select::Node &node);
private:
    void visitAndBranch(const document::select::And &expr) override;
    void visitComparison(const document::select::Compare &expr) override;
    void visitDocumentType(const document::select::DocType &expr) override;
    void visitNotBranch(const document::select::Not &expr) override;
    void visitOrBranch(const document::select::Or &expr) override;
    void visitArithmeticValueNode(const document::select::ArithmeticValueNode &expr) override;
    void visitFunctionValueNode(const document::select::FunctionValueNode &expr) override;
    void visitIdValueNode(const document::select::IdValueNode &expr) override;
    void visitFieldValueNode(const document::select::FieldValueNode &expr) override;
    void invertNode();
    const document::select::Operator &getOperator(const document::select::Operator &op);
    void addNodeCount(const SelectPruner &rhs);
    void setInvalidVal();
    void setInvalidConst();
    void setTernaryConst(bool val);
    void resolveTernaryConst(bool wantInverted);
    bool isInvalidVal() const;
    bool isNullVal() const;
    void swap(SelectPruner &rhs);
};

} // namespace proton
