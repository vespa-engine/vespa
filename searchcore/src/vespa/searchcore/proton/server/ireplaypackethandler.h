// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/serialnum.h>

namespace document { class DocumentTypeRepo; }

namespace proton {

class PutOperation;
class RemoveOperation;
class UpdateOperation;
struct NoopOperation;
class NewConfigOperation;
class DeleteBucketOperation;
class SplitBucketOperation;
class JoinBucketsOperation;
class PruneRemovedDocumentsOperation;
class MoveOperation;
class CreateBucketOperation;
class CompactLidSpaceOperation;

namespace feedoperation { struct IStreamHandler; }

/**
 * Interface used to handle the various feed operations during
 * replay of the transaction log.
 */
struct IReplayPacketHandler
{
    virtual ~IReplayPacketHandler() {}
    virtual void replay(const PutOperation &op) = 0;
    virtual void replay(const RemoveOperation &op) = 0;
    virtual void replay(const UpdateOperation &op) = 0;
    virtual void replay(const NoopOperation &op) = 0;
    virtual void replay(const NewConfigOperation &op) = 0;
    virtual void replay(const DeleteBucketOperation &op) = 0;
    virtual void replay(const SplitBucketOperation &op) = 0;
    virtual void replay(const JoinBucketsOperation &op) = 0;
    virtual void replay(const PruneRemovedDocumentsOperation &op) = 0;
    virtual void replay(const MoveOperation &op) = 0;
    virtual void replay(const CreateBucketOperation &op) = 0;
    virtual void replay(const CompactLidSpaceOperation &op) = 0;
    virtual void check_serial_num(search::SerialNum serial_num) = 0;
    virtual void optionalCommit(search::SerialNum) = 0;
    
    virtual feedoperation::IStreamHandler &getNewConfigStreamHandler() = 0;
    virtual const document::DocumentTypeRepo &getDeserializeRepo() = 0;
};

} // namespace proton

