// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/documentapi/common.h>

namespace documentapi {

/**
 * This class contains the response to a feed command from a single
 * RTC node.
 */
class FeedAnswer {
public:
    /**
     * How the feed command was handled. Be careful about enum
     * ordering as this will be serialized. Add new values at the end.
     */
    enum Handling {
        UNKNOWN = 0,
        HANDLED_OK,
        HANDLED_MISSED_PREV,
        HANDLED_AS_HINT,
        IGNORED_DUP,
        IGNORED_SOF_REALTIME,
        IGNORED_LABEL_MISMATCH,
        ERROR_TOOLOW_INCREMENT,
        ERROR_TOOHIGH_INCREMENT,
        ERROR_INCREMENT_IN_BATCHMODE,
        ERROR_MISSING_SOF_FOR_EOF,
        ERROR_WRONG_SOF_FOR_EOF,
        ERROR_WRITING_LABEL,
        HANDLED_AS_PROBE
    };

public:
    /**
     * Constructs an empty feed answer.
     */
    FeedAnswer();

    /**
     * Constructs a complete feed answer.
     *
     * @param answerCode The code per the enum above.
     * @param wantedIncrement The increment of the current feed transaction.
     * @param recipient The name of the RTC node.
     * @param moreInfo Arbitrary additional info.
     */
    FeedAnswer(int answerCode, int wantedIncrement,
               const string& recipient,
               const string& moreInfo);

    /**
     * Destructor. Frees any allocated resources.
     */
    virtual ~FeedAnswer();

    /**
     * Returns the numerical code of this answer.
     *
     * @return The code.
     */
    int getAnswerCode() const;

    /**
     * Returns the increment of the feed transaction that the RTC is
     * currently processing.
     *
     * @param The increment.
     */
    int getWantedIncrement() const;

    /**
     * Returns the name of the RTC node whose answer this is.
     *
     * @return The recipient name.
     */
    const string& getRecipient() const;

    /**
     * Returns any additional info added to the answer.
     *
     * @return The additional data.
     */
    const string& getMoreInfo() const;

private:
    int _answerCode;        // The code of this answer.
    int _wantedIncrement;   // The increment of the current feed.
    string _recipient; // The name of the RTC node.
    string _moreInfo;  // Any additional data.
};

}


