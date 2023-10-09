// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "unique_issues.h"

namespace search {

void
UniqueIssues::handle(const vespalib::Issue &issue)
{
    _messages.insert(issue.message());
}

}
