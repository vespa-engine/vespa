// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <exception>
#include <string>

namespace ns_log {

/*
 * Exception thrown when parsing a log line into a LogMessage fails.
 */
class BadLogLineException : public std::exception
{
    std::string _message;
public:
    BadLogLineException(std::string message);
    ~BadLogLineException() override;
    const char *what() const noexcept override;
};

}
