// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "feedoperation.h"

namespace proton {

class SpoolerReplayOperation : public FeedOperation
{
private:
    SerialNum _spoolerSerialNum;
protected:
    SpoolerReplayOperation(Type type);
    SpoolerReplayOperation(Type type, SerialNum serialNum, SerialNum spoolerSerialNum);
public:
    ~SpoolerReplayOperation() override {}
    SerialNum getSpoolerSerialNum() const { return _spoolerSerialNum; }
    void serialize(vespalib::nbostream &os) const override;
    void deserialize(vespalib::nbostream &is, const document::DocumentTypeRepo &) override {
        deserialize(is);
    }
    void deserialize(vespalib::nbostream &is);
    virtual vespalib::string toString() const override;
};


/**
 * Indicate that we are starting replaying the spooler log.
 */
class SpoolerReplayStartOperation : public SpoolerReplayOperation
{
public:
    SpoolerReplayStartOperation();
    /**
     * @param serialNum the current serial number of the transaction log.
     * @param spoolerSerialNum the serial number of the first entry of the spooler log replay.
     */
    SpoolerReplayStartOperation(SerialNum serialNum, SerialNum spoolerSerialNum);
};


/**
 * Indicate that we are complete replaying the spooler log.
 */
class SpoolerReplayCompleteOperation : public SpoolerReplayOperation
{
public:
    SpoolerReplayCompleteOperation();
    /**
     * @param serialNum the current serial number of the transaction log.
     * @param spoolerSerialNum the serial number of the last entry of the spooler log replay.
     */
    SpoolerReplayCompleteOperation(SerialNum serialNum, SerialNum spoolerSerialNum);
};

} // namespace proton

