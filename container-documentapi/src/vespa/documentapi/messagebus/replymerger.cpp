// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "replymerger.h"
#include "documentprotocol.h"
#include <vespa/documentapi/messagebus/messages/removedocumentreply.h>
#include <vespa/documentapi/messagebus/messages/updatedocumentreply.h>
#include <vespa/documentapi/messagebus/messages/getdocumentreply.h>
#include <vespa/messagebus/emptyreply.h>
#include <cassert>

namespace documentapi {

ReplyMerger::ReplyMerger()
    : _error(),
      _ignored(),
      _successReply(0),
      _successIndex(0)
{
}

ReplyMerger::~ReplyMerger() {}

ReplyMerger::Result::Result(uint32_t successIdx,
                            std::unique_ptr<mbus::Reply> generatedReply)
    : _generatedReply(std::move(generatedReply)),
      _successIdx(successIdx)
{
}

ReplyMerger::Result::Result(Result&& o)
    : _generatedReply(std::move(o._generatedReply)),
      _successIdx(o._successIdx)
{
}

bool
ReplyMerger::Result::hasGeneratedReply() const
{
    return (_generatedReply.get() != 0);
}

bool
ReplyMerger::Result::isSuccessful() const
{
    return !hasGeneratedReply();
}

std::unique_ptr<mbus::Reply>
ReplyMerger::Result::releaseGeneratedReply()
{
    assert(hasGeneratedReply());
    return std::move(_generatedReply);
}

uint32_t
ReplyMerger::Result::getSuccessfulReplyIndex()
{
    assert(!hasGeneratedReply());
    return _successIdx;
}

void
ReplyMerger::merge(uint32_t idx, const mbus::Reply& r)
{
    if (r.hasErrors()) {
        mergeAllReplyErrors(r);
    } else {
        updateStateWithSuccessfulReply(idx, r);
    }
}

bool
ReplyMerger::resourceWasFound(const mbus::Reply& r) const
{
    switch (r.getType()) {
    case DocumentProtocol::REPLY_REMOVEDOCUMENT:
        return static_cast<const RemoveDocumentReply&>(r).wasFound();
    case DocumentProtocol::REPLY_UPDATEDOCUMENT:
        return static_cast<const UpdateDocumentReply&>(r).wasFound();
    case DocumentProtocol::REPLY_GETDOCUMENT:
        return static_cast<const GetDocumentReply&>(r).getLastModified() != 0;
    default:
        return false;
    }
}

// Precondition: _successReply != 0
bool
ReplyMerger::replyIsBetterThanCurrent(const mbus::Reply& r) const
{
    return resourceWasFound(r) && !resourceWasFound(*_successReply);
}

void
ReplyMerger::setCurrentBestReply(uint32_t idx, const mbus::Reply& r)
{
    _successIndex = idx;
    _successReply = &r;
}

void
ReplyMerger::updateStateWithSuccessfulReply(uint32_t idx, const mbus::Reply& r)
{
    if (!_successReply || replyIsBetterThanCurrent(r)) {
        setCurrentBestReply(idx, r);
    }
}

void
ReplyMerger::mergeAllReplyErrors(const mbus::Reply& r)
{
    if (handleReplyWithOnlyIgnoredErrors(r)) {
        return;
    }
    if (!_error.get()) {
        _error.reset(new mbus::EmptyReply());
    }
    for (uint32_t i = 0; i < r.getNumErrors(); ++i) {
        _error->addError(r.getError(i));
    }
}

bool
ReplyMerger::handleReplyWithOnlyIgnoredErrors(const mbus::Reply& r)
{
    if (DocumentProtocol::hasOnlyErrorsOfType(r, DocumentProtocol::ERROR_MESSAGE_IGNORED)) {
        if (!_ignored.get()) {
            _ignored.reset(new mbus::EmptyReply());
        }
        _ignored->addError(r.getError(0));
        return true;
    }
    return false;
}

bool
ReplyMerger::shouldReturnErrorReply() const
{
    if (_error.get()) {
        return true;
    }
    return (_ignored.get() && !_successReply);
}

std::unique_ptr<mbus::Reply>
ReplyMerger::releaseGeneratedErrorReply()
{
    if (_error.get()) {
        return std::move(_error);
    } else {
        assert(_ignored.get());
        return std::move(_ignored);
    }
}

bool
ReplyMerger::successfullyMergedAtLeastOneReply() const
{
    return (_successReply != 0);
}

ReplyMerger::Result
ReplyMerger::createEmptyReplyResult() const
{
    return Result(0u, std::unique_ptr<mbus::Reply>(new mbus::EmptyReply()));
}

ReplyMerger::Result
ReplyMerger::mergedReply()
{
    std::unique_ptr<mbus::Reply> generated;
    if (shouldReturnErrorReply()) {
        generated = releaseGeneratedErrorReply();
    } else if (!successfullyMergedAtLeastOneReply()) {
        return createEmptyReplyResult();
    }
    return Result(_successIndex, std::move(generated));
}

} // documentapi

