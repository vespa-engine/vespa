// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentreply.h"

namespace documentapi {

class StatBucketReply : public DocumentReply {
private:
    string _results;

public:
    StatBucketReply();
    ~StatBucketReply();
    void setResults(const string& results) { _results = results; }
    const string& getResults() const { return _results; }
    string toString() const override { return "statbucketreply"; }
};

}

