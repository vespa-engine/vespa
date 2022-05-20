// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "fakerewriter.h"
#include <vespa/vespalib/util/stringfmt.h>

namespace juniper
{

struct RewriteHandle
{
    RewriteHandle(std::string& in, uint32_t langid)
        : _s(in), _ls(""), _cnt(0), _langid(langid) {}

    std::string& next()
    {
	if (_cnt > 3 || _langid > 4)
            _ls = "";
	else
            _ls = vespalib::make_string("%s%d", _s.c_str(), _cnt++);
	return _ls;
    }
    std::string _s;
    std::string _ls;
    int _cnt;
    uint32_t _langid;
};
} // end namespace juniper

using namespace juniper;

const char* FakeRewriter::Name() const
{
    return _name.c_str();
}


RewriteHandle* FakeRewriter::Rewrite(uint32_t langid, const char* term)
{
    std::string t(term);
    if (langid > 4) return NULL;
    return new RewriteHandle(t, langid);
}

RewriteHandle* FakeRewriter::Rewrite(uint32_t langid, const char* term, size_t length)
{
    std::string t(term, length);
    if (langid > 4) return NULL;
    return new RewriteHandle(t, langid);
}


const char* FakeRewriter::NextTerm(RewriteHandle* exp, size_t& length)
{
    std::string& t = exp->next();
    if (t.size() == 0)
    {
        delete exp;
        return NULL;
    }
    length = t.size();
    return t.c_str();
}
