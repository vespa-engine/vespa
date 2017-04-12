// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "feedoperation.h"
#include <vespa/vespalib/objects/nbostream.h>

namespace proton {

class NewConfigOperation : public FeedOperation
{
public:
    struct IStreamHandler {
        virtual ~IStreamHandler() {}
        virtual void serializeConfig(SerialNum serialNum,
                                     vespalib::nbostream &os) = 0;
        virtual void deserializeConfig(SerialNum serialNum,
                                       vespalib::nbostream &is) = 0;
    };
private:
    IStreamHandler &_streamHandler;
public:
    NewConfigOperation(SerialNum serialNum,
                       IStreamHandler &streamHandler);
    virtual ~NewConfigOperation() {}
    virtual void serialize(vespalib::nbostream &os) const override;
    virtual void deserialize(vespalib::nbostream &is,
                             const document::DocumentTypeRepo &repo) override;
    virtual vespalib::string toString() const override;
};

} // namespace proton

