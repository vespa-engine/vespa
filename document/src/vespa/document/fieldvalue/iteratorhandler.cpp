// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include "iteratorhandler.h"

namespace document::fieldvalue {

IteratorHandler::~IteratorHandler() { }


void
IteratorHandler::handlePrimitive(uint32_t fid, const FieldValue & fv) {
    onPrimitive(fid, Content(fv, getWeight()));
}
bool
IteratorHandler::handleComplex(const FieldValue & fv) {
    return onComplex(Content(fv, getWeight()));
}
void
IteratorHandler::handleCollectionStart(const FieldValue & fv) {
    onCollectionStart(Content(fv, getWeight()));
}
void
IteratorHandler::handleCollectionEnd(const FieldValue & fv) {
    onCollectionEnd(Content(fv, getWeight()));
}
void
IteratorHandler::handleStructStart(const FieldValue & fv) {
    onStructStart(Content(fv, getWeight()));
}
void
IteratorHandler::handleStructEnd(const FieldValue & fv) {
    onStructEnd(Content(fv, getWeight()));
}

void
IteratorHandler::onPrimitive(uint32_t fid, const Content & fv) {
    (void) fid;
    (void) fv;
}

}

