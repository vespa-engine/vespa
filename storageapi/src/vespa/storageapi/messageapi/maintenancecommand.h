// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bucketinfocommand.h"

namespace storage {
namespace api {

class MaintenanceCommand : public BucketInfoCommand
{
public:
    MaintenanceCommand(const MessageType& type, const document::Bucket &bucket)
        : BucketInfoCommand(type, bucket)
    {}
    MaintenanceCommand(MaintenanceCommand &&) = default;
    MaintenanceCommand & operator = (MaintenanceCommand &&) = default;
    ~MaintenanceCommand();

    const vespalib::string& getReason() const { return _reason; };
    void setReason(vespalib::stringref  reason) { _reason = reason; };
protected:
    vespalib::string _reason;
};

}
}
