// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "bucketinfocommand.h"

namespace storage {
namespace api {

class MaintenanceCommand : public BucketInfoCommand
{
public:
    MaintenanceCommand(const MessageType& type, const document::BucketId& id)
        : BucketInfoCommand(type, id)
    {}
    MaintenanceCommand(MaintenanceCommand &&) = default;
    MaintenanceCommand & operator = (MaintenanceCommand &&) = default;
    ~MaintenanceCommand();

    const vespalib::string& getReason() const { return _reason; };
    void setReason(const vespalib::stringref & reason) { _reason = reason; };
protected:
    vespalib::string _reason;
};

}
}
