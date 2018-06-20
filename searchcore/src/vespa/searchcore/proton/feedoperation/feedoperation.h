// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/common/serialnum.h>
#include <vespa/vespalib/stllike/string.h>

namespace document { class DocumentTypeRepo; }
namespace vespalib { class nbostream; }
namespace proton {

class FeedOperation
{
public:
    typedef search::SerialNum SerialNum;
    typedef std::shared_ptr<FeedOperation> SP;
    typedef std::unique_ptr<FeedOperation> UP;

    /*
     * Enumeration of feed operations. UPDATE_42 is partial update
     * without support for field path updates (kept to support replay
     * of old transaction logs).  UPDATE is partial update with
     * support for field paths updates.
     */
    enum Type {
        PUT                     = 1,
        REMOVE                  = 2,
        REMOVE_BATCH            = 3,
        UPDATE_42               = 4,
        NOOP                    = 5,
        NEW_CONFIG              = 6,
        WIPE_HISTORY            = 7,
        DELETE_BUCKET           = 9,
        SPLIT_BUCKET            = 10,
        JOIN_BUCKETS            = 11,
        PRUNE_REMOVED_DOCUMENTS = 12,
        SPOOLER_REPLAY_START    = 13,
        SPOOLER_REPLAY_COMPLETE = 14,
        MOVE                    = 15,
        CREATE_BUCKET           = 16,
        COMPACT_LID_SPACE       = 17,
        UPDATE                  = 18
    };

private:
    Type _type;
    SerialNum _serialNum;

public:
    FeedOperation(Type type);
    virtual ~FeedOperation() {}
    Type getType() const { return _type; }
    void setSerialNum(SerialNum serialNum) { _serialNum = serialNum; }
    SerialNum getSerialNum() const { return _serialNum; }
    virtual void serialize(vespalib::nbostream &os) const = 0;
    virtual void deserialize(vespalib::nbostream &is, const document::DocumentTypeRepo &repo) = 0;
    virtual vespalib::string toString() const = 0;
};

} // namespace proton

