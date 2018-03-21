// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "filestorhandler.h"
#include "filestorhandlerimpl.h"

namespace storage {

FileStorHandler::FileStorHandler(MessageSender& sender,
                                 FileStorMetrics& metrics,
                                 const spi::PartitionStateList& partitions,
                                 ServiceLayerComponentRegister& compReg)
    : _impl(new FileStorHandlerImpl(sender, metrics, partitions, compReg))
{
}

FileStorHandler::~FileStorHandler()
{
    delete _impl;
}

void
FileStorHandler::flush(bool flushMerges)
{
    _impl->flush(flushMerges);
}

void
FileStorHandler::setDiskState(uint16_t disk, DiskState state)
{
    _impl->setDiskState(disk, state);
}

FileStorHandler::DiskState
FileStorHandler::getDiskState(uint16_t disk)
{
    return _impl->getDiskState(disk);
}

void
FileStorHandler::close()
{
    _impl->close();
}

ResumeGuard
FileStorHandler::pause()
{
    return _impl->pause();
}

bool
FileStorHandler::schedule(const api::StorageMessage::SP& msg, uint16_t thread)
{
    return _impl->schedule(msg, thread);
}

FileStorHandler::LockedMessage
FileStorHandler::getNextMessage(uint16_t thread)
{
    return _impl->getNextMessage(thread);
}

FileStorHandler::LockedMessage &
FileStorHandler::getNextMessage(uint16_t thread, LockedMessage& lck)
{
    return _impl->getNextMessage(thread, lck);
}

FileStorHandler::BucketLockInterface::SP
FileStorHandler::lock(const document::Bucket& bucket, uint16_t disk)
{
    return _impl->lock(bucket, disk);
}

void
FileStorHandler::remapQueueAfterDiskMove(
        const document::Bucket& bucket,
        uint16_t sourceDisk, uint16_t targetDisk)
{
    RemapInfo target(bucket, targetDisk);

    _impl->remapQueue(RemapInfo(bucket, sourceDisk), target, FileStorHandlerImpl::MOVE);
}

void
FileStorHandler::remapQueueAfterJoin(const RemapInfo& source,RemapInfo& target)
{
    _impl->remapQueue(source, target, FileStorHandlerImpl::JOIN);
}

void
FileStorHandler::remapQueueAfterSplit(const RemapInfo& source,RemapInfo& target1, RemapInfo& target2)
{
    _impl->remapQueue(source, target1, target2, FileStorHandlerImpl::SPLIT);
}

void
FileStorHandler::failOperations(const document::Bucket &bucket, uint16_t fromDisk, const api::ReturnCode& err)
{
    _impl->failOperations(bucket, fromDisk, err);
}

void
FileStorHandler::sendCommand(const api::StorageCommand::SP& msg)
{
    _impl->sendCommand(msg);
}

void
FileStorHandler::sendReply(const api::StorageReply::SP& msg)
{
    _impl->sendReply(msg);
}

void
FileStorHandler::getStatus(std::ostream& out, const framework::HttpUrlPath& path) const
{
    _impl->getStatus(out, path);
}

uint32_t
FileStorHandler::getQueueSize() const
{
    return _impl->getQueueSize();
}

uint32_t
FileStorHandler::getQueueSize(uint16_t disk) const
{
    return _impl->getQueueSize(disk);
}

void
FileStorHandler::addMergeStatus(const document::Bucket& bucket, MergeStatus::SP ms)
{
    return _impl->addMergeStatus(bucket, ms);
}

MergeStatus&
FileStorHandler::editMergeStatus(const document::Bucket& bucket)
{
    return _impl->editMergeStatus(bucket);
}

bool
FileStorHandler::isMerging(const document::Bucket& bucket) const
{
    return _impl->isMerging(bucket);
}

uint32_t
FileStorHandler::getNumActiveMerges() const
{
    return _impl->getNumActiveMerges();
}

void
FileStorHandler::clearMergeStatus(const document::Bucket& bucket,
                                  const api::ReturnCode& code)
{
    return _impl->clearMergeStatus(bucket, &code);
}

void
FileStorHandler::clearMergeStatus(const document::Bucket& bucket)
{
    return _impl->clearMergeStatus(bucket, 0);
}

void
FileStorHandler::abortQueuedOperations(const AbortBucketOperationsCommand& cmd)
{
    _impl->abortQueuedOperations(cmd);
}

void
FileStorHandler::setGetNextMessageTimeout(uint32_t timeout)
{
    _impl->setGetNextMessageTimeout(timeout);
}

std::string
FileStorHandler::dumpQueue(uint16_t disk) const
{
    return _impl->dumpQueue(disk);
}

} // storage
