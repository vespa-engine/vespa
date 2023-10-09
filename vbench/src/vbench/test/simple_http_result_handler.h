// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

#include <vbench/http/http_result_handler.h>
#include <vespa/vespalib/data/simple_buffer.h>

namespace vbench {

class SimpleHttpResultHandler : public HttpResultHandler
{
public:
    using SimpleBuffer = vespalib::SimpleBuffer;
private:
    std::vector<std::pair<string, string> > _headers;
    SimpleBuffer                            _content;
    std::vector<string>                     _failures;

public:
    SimpleHttpResultHandler();
    ~SimpleHttpResultHandler();
    virtual void handleHeader(const string &name, const string &value) override;
    virtual void handleContent(const Memory &data) override;
    virtual void handleFailure(const string &reason) override;
    const std::vector<std::pair<string, string> > &headers() const {
        return _headers;
    }
    Memory content() const { return _content.get(); }
    const std::vector<string> &failures() const { return _failures; }
};

} // namespace vbench

