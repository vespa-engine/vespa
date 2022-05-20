// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "specialtokenregistry.h"

namespace {

class QueryVisitor : public IQueryExprVisitor {
private:
    juniper::SpecialTokenRegistry & _registry;

public:
    QueryVisitor(juniper::SpecialTokenRegistry & registry) : _registry(registry) {}
    void VisitQueryNode(QueryNode *) override { }
    void RevisitQueryNode(QueryNode *) override { }
    void VisitQueryTerm(QueryTerm * t) override {
        if (t->isSpecialToken()) {
            _registry.addSpecialToken(t);
        }
    }
};

}

namespace juniper {


SpecialTokenRegistry::CharStream::CharStream(const char * srcBuf, const char * srcEnd,
                                             ucs4_t * dstBuf, ucs4_t * dstEnd) :

    _srcBuf(srcBuf),
    _srcItr(srcBuf),
    _srcEnd(srcEnd),
    _nextStart(srcBuf),
    _dstBuf(dstBuf),
    _dstItr(dstBuf),
    _dstEnd(dstEnd),
    _isStartWordChar(false)
{
    if (srcBuf < srcEnd) {
        ucs4_t ch = getNextChar();
        _nextStart = _srcItr;
        _isStartWordChar = Fast_UnicodeUtil::IsWordChar(ch);
        reset();
    }
}

bool
SpecialTokenRegistry::CharStream::resetAndInc()
{
    _srcItr = _nextStart;
    if (hasMoreChars()) {
        ucs4_t ch = getNextChar();
        _isStartWordChar = Fast_UnicodeUtil::IsWordChar(ch);
        _srcBuf = _nextStart; // move start to next character
        _nextStart = _srcItr; // move next start to the next next character
        reset();
        return true;
    } else {
        return false;
    }
}


bool
SpecialTokenRegistry::match(const ucs4_t * qsrc, const ucs4_t * qend, CharStream & stream) const
{
    for (; (qsrc < qend) && stream.hasMoreChars(); ++qsrc) {
        ucs4_t ch = stream.getNextChar();
        if (ch != *qsrc) {
            return false;
        }
    }
    return (qsrc == qend);
}

SpecialTokenRegistry::SpecialTokenRegistry(QueryExpr * query) :
    _specialTokens()
{
    QueryVisitor qv(*this);
    query->Accept(qv); // find the special tokens
}

const char *
SpecialTokenRegistry::tokenize(const char * buf, const char * bufend,
                               ucs4_t * dstbuf, ucs4_t * dstbufend,
                               const char * & origstart, size_t & tokenlen) const
{
    CharStream stream(buf, bufend, dstbuf, dstbufend);
    bool foundWordChar = false;
    while(!foundWordChar && stream.hasMoreChars() && stream.hasMoreSpace()) {
        for (size_t i = 0; i < _specialTokens.size(); ++i) {
            const ucs4_t * qsrc = _specialTokens[i]->ucs4_term();
            const ucs4_t * qend = qsrc + _specialTokens[i]->ucs4_len;
            // try to match the given special token with the input stream
            if (match(qsrc, qend, stream)) {
                origstart = stream.getSrcStart();
                tokenlen = stream.getNumChars();
                return stream.getSrcItr();
            }
            stream.reset();
        }
        foundWordChar = stream.isStartWordChar();
        stream.resetAndInc();
    }

    return NULL;
}

} // namespace juniper


