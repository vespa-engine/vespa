// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/messagebus/reply.h>

namespace documentapi {

class ReplyMerger
{
public:
    class Result {
        friend class ReplyMerger;

        std::unique_ptr<mbus::Reply> _generatedReply;
        uint32_t _successIdx;

        Result(uint32_t successIdx, std::unique_ptr<mbus::Reply> generatedReply);
    public:
        Result(Result&& o) noexcept
            : _generatedReply(std::move(o._generatedReply)),
              _successIdx(o._successIdx)
        { }

        bool hasGeneratedReply() const;
        bool isSuccessful() const;
        std::unique_ptr<mbus::Reply> releaseGeneratedReply();
        uint32_t getSuccessfulReplyIndex() const;
    };
private:
    std::unique_ptr<mbus::Reply> _error;
    std::unique_ptr<mbus::Reply> _ignored;
    const mbus::Reply* _successReply;
    uint32_t _successIndex;

    void mergeAllReplyErrors(const mbus::Reply&);
    bool handleReplyWithOnlyIgnoredErrors(const mbus::Reply& r);
    bool shouldReturnErrorReply() const;
    std::unique_ptr<mbus::Reply> releaseGeneratedErrorReply();
    bool replyIsBetterThanCurrent(const mbus::Reply& r) const;
    void setCurrentBestReply(uint32_t idx, const mbus::Reply& r);
    void updateStateWithSuccessfulReply(uint32_t idx, const mbus::Reply& r);
    bool successfullyMergedAtLeastOneReply() const;
    static Result createEmptyReplyResult();
    static bool resourceWasFound(const mbus::Reply& r);
public:
    ReplyMerger();
    ~ReplyMerger();

    void merge(uint32_t idx, const mbus::Reply&);
    Result mergedReply();
};

} // documentapi
