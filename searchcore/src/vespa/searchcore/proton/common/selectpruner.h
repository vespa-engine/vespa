// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/select/node.h>
#include <vespa/document/select/valuenode.h>
#include <vespa/document/select/cloningvisitor.h>
#include <vespa/document/select/operator.h>
#include <vespa/document/select/resultset.h>

namespace search { class IAttributeManager; }

namespace document { class IDocumentTypeRepo; }

namespace proton {

class SelectPrunerBase
{
protected:
    const std::string &_docType;
    const search::IAttributeManager *_amgr;
    const document::Document &_emptyDoc;
    const document::IDocumentTypeRepo &_repo;
    bool _hasFields;
    bool _hasDocuments;

public:
    SelectPrunerBase(const std::string &docType,
                     const search::IAttributeManager *amgr,
                     const document::Document &emptyDoc,
                     const document::IDocumentTypeRepo &repo,
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
    using NodeUP = document::select::Node::UP;
    using ValueNodeUP = document::select::ValueNode::UP;
    uint32_t _attrFieldNodes;
public:
    SelectPruner(const std::string &docType,
                 const search::IAttributeManager *amgr,
                 const document::Document &emptyDoc,
                 const document::IDocumentTypeRepo &repo,
                 bool hasFields,
                 bool hasDocuments);

    explicit SelectPruner(const SelectPruner *rhs);
    ~SelectPruner() override;

    [[nodiscard]] uint32_t getFieldNodes() const noexcept { return _fieldNodes; }
    [[nodiscard]] uint32_t getAttrFieldNodes() const noexcept { return _attrFieldNodes; }
    [[nodiscard]] const document::select::ResultSet & getResultSet() const noexcept { return _resultSet; }
    [[nodiscard]] bool isFalse() const;
    [[nodiscard]] bool isTrue() const;
    [[nodiscard]] bool isInvalid() const;
    [[nodiscard]] bool isConst() const;
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
    void set_null_value_node();
    void resolveTernaryConst(bool wantInverted);
    [[nodiscard]] bool isInvalidVal() const;
    [[nodiscard]] bool isNullVal() const;
    void swap(SelectPruner &rhs);
};

} // namespace proton
