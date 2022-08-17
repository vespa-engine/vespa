// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
//
#include "tokenizer.h"
#include "juniperdebug.h"
#include <vespa/fastlib/text/wordfolder.h>

#include <vespa/log/log.h>
LOG_SETUP(".juniper.tokenizer");

JuniperTokenizer::JuniperTokenizer(const Fast_WordFolder* wordfolder,
				   const char* text, size_t len, ITokenProcessor* successor,
                                   const juniper::SpecialTokenRegistry * registry) :
    _wordfolder(wordfolder), _text(text), _len(len), _successor(successor), _registry(registry),
    _charpos(0), _wordpos(0)
{ }


void JuniperTokenizer::SetText(const char* text, size_t len)
{
    _text = text;
    _len = len;
    _charpos = 0;
    _wordpos = 0;
}


// Scan the input and dispatch to the successor
void JuniperTokenizer::scan()
{
    ITokenProcessor::Token token;

    const char* src = _text;
    const char* src_end = _text + _len;
    const char* startpos = NULL;
    ucs4_t* dst = _buffer;
    ucs4_t* dst_end = dst + TOKEN_DSTLEN;
    size_t result_len;

    while (src < src_end)
    {
        if (_registry == NULL) {
            // explicit prefetching seems to have negative effect with many threads
            //  FastOS_Prefetch::NT(const_cast<void *>((const void *)(src + 32)));
            src = _wordfolder->UCS4Tokenize(src, src_end, dst, dst_end, startpos, result_len);
        } else {
            const char * tmpSrc = _registry->tokenize(src, src_end, dst, dst_end, startpos, result_len);
            if (tmpSrc == NULL) {
                src = _wordfolder->UCS4Tokenize(src, src_end, dst, dst_end, startpos, result_len);
            } else {
                src = tmpSrc;
            }
        }
        if (dst[0] == 0) break;
        token.curlen = result_len;
        token.token = dst;
        token.wordpos = _wordpos++;
        token.bytepos = startpos - _text;
        token.bytelen = src - startpos;
        LOG(debug, "curlen %d, bytepos %" PRId64 ", bytelen %d",
            token.curlen, static_cast<int64_t>(token.bytepos), token.bytelen);
        // NB! not setting charlen/charpos/_utf8pos/_utf8len yet...!
        _successor->handle_token(token);
    }
    token.bytepos = _len;
    token.bytelen = 0;
    token.token = NULL;
    _successor->handle_end(token);
}
