// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "feedoperation.h"

namespace proton {

    namespace feedoperation {
        struct IStreamHandler {
            virtual ~IStreamHandler() {}
            virtual void serializeConfig(search::SerialNum serialNum, vespalib::nbostream &os) = 0;
            virtual void deserializeConfig(search::SerialNum serialNum, vespalib::nbostream &is) = 0;
        };
    }

class NewConfigOperation : public FeedOperation
{
public:
    using IStreamHandler = feedoperation::IStreamHandler;
private:
    IStreamHandler &_streamHandler;
public:
    NewConfigOperation(SerialNum serialNum, IStreamHandler &streamHandler);
    ~NewConfigOperation() override {}
    void serialize(vespalib::nbostream &os) const override;
    void deserialize(vespalib::nbostream &is, const document::DocumentTypeRepo &repo) override;
    vespalib::string toString() const override;
};

} // namespace proton

