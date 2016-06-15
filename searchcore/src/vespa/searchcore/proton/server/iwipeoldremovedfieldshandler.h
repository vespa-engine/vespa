// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton {

class IWipeOldRemovedFieldsHandler
{
public:
    virtual void wipeOldRemovedFields(fastos::TimeStamp wipeTimeLimit) = 0;

    virtual ~IWipeOldRemovedFieldsHandler() {}
};

} // namespace proton

