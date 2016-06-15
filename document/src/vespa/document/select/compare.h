// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class document::select::Compare
 * @ingroup select
 *
 * @brief Node comparing two values
 *
 * @author H�kon Humberset
 * @date 2007-04-20
 * @version $Id$
 */

#pragma once

#include <memory>
#include "node.h"
#include "operator.h"
#include <vespa/document/bucket/bucketidfactory.h>

namespace document {
namespace select {

class ValueNode;

class Compare : public Node
{
private:
    std::unique_ptr<ValueNode> _left;
    std::unique_ptr<ValueNode> _right;
    const Operator& _operator;
    const BucketIdFactory& _bucketIdFactory;

    bool isLeafNode() const { return false; }
public:
    Compare(std::unique_ptr<ValueNode> left, const Operator& op,
            std::unique_ptr<ValueNode> right,
            const BucketIdFactory& bucketidfactory);
    virtual ~Compare();

    virtual ResultList
    contains(const Context &context) const;

    virtual ResultList
    trace(const Context &context,
          std::ostream& trace) const;

    virtual void print(std::ostream&, bool verbose,
                       const std::string& indent) const;
    virtual void visit(Visitor& v) const;

    const Operator& getOperator() const { return _operator; }

    const ValueNode& getLeft() const { return *_left; }
    const ValueNode& getRight() const { return *_right; }

    Node::UP clone() const;

    const BucketIdFactory &
    getBucketIdFactory(void) const
    {
        return _bucketIdFactory;
    }
};

} // select
} // document

