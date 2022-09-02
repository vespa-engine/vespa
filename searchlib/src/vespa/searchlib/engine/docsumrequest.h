// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "request.h"
#include "lazy_source.h"
#include <vespa/document/base/globalid.h>
#include <vespa/searchlib/common/hitrank.h>

namespace search::engine {

class DocsumRequest : public Request
{
public:
    using UP = std::unique_ptr<DocsumRequest>;
    using SP = std::shared_ptr<DocsumRequest>;
    using Source = LazySource<DocsumRequest>;
    using FieldList = std::vector<vespalib::string>;

    class Hit {
    public:
        Hit() noexcept : gid(), docid(0) {}
        explicit Hit(const document::GlobalId & gid_) noexcept : gid(gid_), docid(0) {}

        document::GlobalId gid;
        mutable uint32_t  docid; // converted in backend
    };

    vespalib::string  resultClassName;
    std::vector<Hit>  hits;
    std::vector<char> sessionId;

    DocsumRequest();
    explicit DocsumRequest(RelativeTime relativeTime);
    ~DocsumRequest() override;
    const FieldList & getFields() const { return _fields; }
    void setFields(FieldList fields) { _fields = std::move(fields); }
private:
    FieldList _fields;
};

}
