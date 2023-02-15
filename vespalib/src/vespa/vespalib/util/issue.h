// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib {

/**
 * An Issue is an error encountered during program execution that will
 * not affect the program flow. It is like an exception that is not
 * (re-)thrown, but rather logged as a warning, or like an error that
 * augments the computation result instead of replacing it. Issues are
 * reported by the code identifying that something is wrong; similar
 * to logging a warning. Issues are handled by an Issue::Handler that
 * is bound to the current thread; similar to try/catch used by
 * exceptions, but rather than disrupting the program flow we only
 * capture any issues encountered along the way. Thread-local data
 * structures are used to match the reporting of an issue with its
 * appropriate handler, making it possible to handle issues across
 * arbitrary synchronous API boundaries without changing the APIs to
 * explicitly wire all issues through them.
 *
 * An Issue object represents a single issue. The static 'report'
 * functions are used to report an issue. The static 'listen' function
 * is used to bind an Issue::Handler to the current thread using a
 * special object that should reside on the stack and unbinds the
 * handler when destructed. Handler bindings can be nested. Issue
 * reports will be routed to the last handler bound to the thread.
 *
 * Note that the objects binding handlers to threads must not be
 * destructed out of order; just let them go out of scope.
 **/
class Issue
{
private:
    vespalib::string _message;
public:
    Issue(vespalib::string message);
    const vespalib::string &message() const { return _message; }
    struct Handler {
        virtual void handle(const Issue &issue) = 0;
        virtual ~Handler() = default;
    };
    class Binding
    {
    public:
        struct Link {
            Handler &handler;
            Link *next;
        };
    private:
        Link _link;
    public:
        Binding(Handler &handler);
        Binding(Binding &&) = delete;
        Binding(const Binding &) = delete;
        Binding &operator=(Binding &&) = delete;
        Binding &operator=(const Binding &) = delete;
        ~Binding();
    };
    static void report(const Issue &issue);
    static Binding listen(Handler &handler);
    static void report(vespalib::string msg);
    static void report(const std::exception &e);
    static void report(const char *format, ...) __attribute__ ((format (printf,1,2)));
};

}
