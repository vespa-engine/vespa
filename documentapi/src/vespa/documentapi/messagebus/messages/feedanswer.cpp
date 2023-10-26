// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "feedanswer.h"

namespace documentapi {

FeedAnswer::FeedAnswer() :
    _answerCode(0),
    _wantedIncrement(0),
    _recipient(""),
    _moreInfo("")
{
    // empty
}

FeedAnswer::FeedAnswer(int answerCode,
		       int wantedIncrement,
		       const string& recipient,
		       const string& moreInfo) :
     _answerCode(answerCode),
     _wantedIncrement(wantedIncrement),
     _recipient(recipient),
     _moreInfo(moreInfo)
{
    // empty
}

FeedAnswer::~FeedAnswer()
{
    // empty
}

int
FeedAnswer::getAnswerCode() const
{
    return _answerCode;
}

int
FeedAnswer::getWantedIncrement() const
{
    return _wantedIncrement;
}

const string&
FeedAnswer::getRecipient() const
{
    return _recipient;
}

const string&
FeedAnswer::getMoreInfo() const
{
    return _moreInfo;
}

}
