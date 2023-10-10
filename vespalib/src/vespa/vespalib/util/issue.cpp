// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "issue.h"
#include "stringfmt.h"

#include <vespa/log/log.h>
LOG_SETUP(".vespalib.issue");

namespace vespalib {

namespace {

using Link = Issue::Binding::Link;

struct LogIssues : Issue::Handler {
    void handle(const Issue &issue) override {
        LOG(warning, "%s", issue.message().c_str());
    }
};

Link *get_root() {
    static LogIssues log_issues;
    static Link root{log_issues, nullptr};
    return &root;
}

Link **get_head() {
    thread_local Link *head = get_root();
    return &head;
}

} // <unnamed>

Issue::Issue(vespalib::string message)
  : _message(std::move(message))
{
}

Issue::Binding::Binding(Handler &handler)
  : _link{handler, nullptr}
{
    Link **head = get_head();
    _link.next = *head;
    *head = &_link;
}

Issue::Binding::~Binding()
{
    Link **head = get_head();
    LOG_ASSERT(*head == &_link);
    *head = (*head)->next;
}

void
Issue::report(const Issue &issue)
{
    (*get_head())->handler.handle(issue);
}

Issue::Binding
Issue::listen(Handler &handler)
{
    return Binding(handler);
}

void
Issue::report(vespalib::string msg)
{
    report(Issue(std::move(msg)));
}

void
Issue::report(const std::exception &e)
{
    report(Issue(e.what()));
}

void
Issue::report(const char *format, ...)
{
    va_list ap;
    va_start(ap, format);
    vespalib::string msg = make_string_va(format, ap);
    va_end(ap);
    report(Issue(std::move(msg)));
}

}
