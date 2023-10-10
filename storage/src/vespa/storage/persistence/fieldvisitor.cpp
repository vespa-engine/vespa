// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// @author Vegard Sjonfjell

#include "fieldvisitor.h"
#include <vespa/document/select/valuenodes.h>

namespace storage {

FieldVisitor::~FieldVisitor() = default;

void FieldVisitor::visitFieldValueNode(const document::select::FieldValueNode & node) {
    _fields.add(&_docType.getField(node.getRealFieldName()));
}

void FieldVisitor::visitComparison(const document::select::Compare & node) {
    visitBothBranches(node);
}

void FieldVisitor::visitAndBranch(const document::select::And & node) {
    visitBothBranches(node);
}

void FieldVisitor::visitOrBranch(const document::select::Or & node) {
    visitBothBranches(node);
}

void FieldVisitor::visitNotBranch(const document::select::Not & node) {
    node.getChild().visit(*this);
}

} // storage
