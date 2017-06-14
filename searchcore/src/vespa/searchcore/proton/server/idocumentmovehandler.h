// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton
{

class MoveOperation;


struct IDocumentMoveHandler
{
    virtual void handleMove(MoveOperation &op) = 0;
    virtual ~IDocumentMoveHandler() {}
};


} // namespace proton

