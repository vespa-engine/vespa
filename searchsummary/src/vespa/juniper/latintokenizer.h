// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
*****************************************************************************
* @author Bård Kvalheim
* @date    Creation date: 2001-12-07
*
* A configurable tokenizer template that accepts two predicates: One to
* determine separator symbols and one to determine punctuation symbols. A
* typedef is defined that uses isspace/1 and ispunct/1.
*
* This tokenizer does not alter the text, and does not copy it.
*
* This tokenizer is not meant to be used as a real tokenizer for all
* languages. It is only a fast and simple latin tokenizer, intended for
* very basic applications.
*
* The tokens are returned as (char *, char *, bool) triples.  The two
* first elements delimit the token string, while the third element is
* true if the token is a punctuation symbol.
*
* If the last character in the input text is a punctuation symbol, the last
* token is the following:
*
*    text = " something bl bla ."
*
*    token.first        -> .
*    token.second       -> \0
*    token._punctuation = true;
*
*  In other words, token.second can point to the terminating '\0' in the input
*  text.
*
*****************************************************************************/

#pragma once

#include <cctype>
#include <cstring>

/**
*****************************************************************************
* A simple tokenizer. See description above.
*
* @class   Fast_LatinTokenizer
* @author Bård Kvalheim
* @date    Creation date: 2001-12-07
*****************************************************************************/

template <typename IsSeparator, typename IsPunctuation>
class Fast_LatinTokenizer {
private:
    Fast_LatinTokenizer(const Fast_LatinTokenizer &);
    Fast_LatinTokenizer& operator=(const Fast_LatinTokenizer &);

public:

    /** Helper class. */
    class Fast_Token {
    public:

        /** Member variables. */
        char *first;        // Points to start of token. Named 'first' for std::pair compatibility.
        char *second;       // Points to end of token.  Named 'second' for std::pair compatibility.
        bool  _punctuation; // Is the token a punctuation symbol?

        /** Constructors. */
        Fast_Token(char *begin, char *end, bool punctuation) : first(begin), second(end), _punctuation(punctuation) {}
        Fast_Token() : first(NULL), second(NULL), _punctuation(false) {}
        Fast_Token(const Fast_Token &other)
            : first(other.first),
              second(other.second),
              _punctuation(other._punctuation)
        {
        }
        Fast_Token& operator=(const Fast_Token &other)
        {
            first = other.first;
            second = other.second;
            _punctuation = other._punctuation;
            return *this;
        }

    };

    /** Constructors/destructor. */
    Fast_LatinTokenizer();
    explicit Fast_LatinTokenizer(char *text);
    Fast_LatinTokenizer(char *text, size_t length);
    virtual ~Fast_LatinTokenizer();

    /** Constructors, sort of. */
    void           SetNewText(char *text);
    void           SetNewText(char *text, size_t length);

    /** Are there any more tokens left? */
    bool           MoreTokens();

    /** Return next token. */
    Fast_Token     GetNextToken();

    /** Return text buffer. */
    char          *GetOriginalText();

    /** Observers in case we need not perform some action specific
     *  to the IsSeparator or IsPunctuation implementations
     *  (such as extra initialization or statistics gathering or...)
     */
    IsPunctuation& GetIsPunctuation() { return _isPunctuation; }
    IsSeparator&   GetIsSeparator()   { return _isSeparator;   }

private:

    /** Member variables. */
    char          *_org;           // Holds the original text buffer.
    char          *_next;          // Points to the current buffer position.
    char          *_end;           // Points to the end of the buffer.
    bool           _moreTokens;    // More text to process?
    IsSeparator    _isSeparator;   // Separator symbol predicate.
    IsPunctuation  _isPunctuation; // Punctuation symbol predicate.

    /** Helper methods. */
    void           SkipBlanks();

};

/**
*****************************************************************************
* Default constructor.
*
* @author Bård Kvalheim
*****************************************************************************/

template <typename IsSeparator, typename IsPunctuation>
Fast_LatinTokenizer<IsSeparator, IsPunctuation>::Fast_LatinTokenizer() :
    _org(NULL),
    _next(NULL),
    _end(NULL),
    _moreTokens(false),
    _isSeparator(),
    _isPunctuation()
{
}

/**
*****************************************************************************
* Constructor. Accepts a '\0' terminated text buffer.
*
* @param  text
* @author Bård Kvalheim
*****************************************************************************/

template <typename IsSeparator, typename IsPunctuation>
Fast_LatinTokenizer<IsSeparator, IsPunctuation>::Fast_LatinTokenizer(char *text) :
    _org(NULL),
    _next(NULL),
    _end(NULL),
    _moreTokens(false),
    _isSeparator(),
    _isPunctuation()
{
    SetNewText(text);
}

/**
*****************************************************************************
* Constructor. Accepts a text buffer and the buffer length
*
* @param  text
* @param  length
* @author Bård Kvalheim
*****************************************************************************/

template <typename IsSeparator, typename IsPunctuation>
Fast_LatinTokenizer<IsSeparator, IsPunctuation>::Fast_LatinTokenizer(char *text, size_t length)
    : _org(NULL),
      _next(NULL),
      _end(NULL),
      _moreTokens(false),
      _isSeparator(),
      _isPunctuation()
{
    SetNewText(text, length);
}

/**
*****************************************************************************
* Destructor.
*
* @author Bård Kvalheim
*****************************************************************************/

template <typename IsSeparator, typename IsPunctuation>
Fast_LatinTokenizer<IsSeparator, IsPunctuation>::~Fast_LatinTokenizer() {
}

/**
*****************************************************************************
* Sets a new '\0' terminated string.
*
* @param  text
* @author Bård Kvalheim
*****************************************************************************/

template <typename IsSeparator, typename IsPunctuation>
void
Fast_LatinTokenizer<IsSeparator, IsPunctuation>::SetNewText(char *text) {

    _org        = text;
    _next       = text;
    _moreTokens = text != NULL;
    _end        = NULL;
}

/**
*****************************************************************************
* Sets a new string, given the text buffer and its length.
*
* @param  text
* @param  length
* @author Bård Kvalheim
*****************************************************************************/

template <typename IsSeparator, typename IsPunctuation>
void
Fast_LatinTokenizer<IsSeparator, IsPunctuation>::SetNewText(char *text, size_t length) {

    _org        = text;
    _next       = text;
    _moreTokens = text != NULL;
    _end        = (_next ? _next + length : NULL);
}

/**
*****************************************************************************
* Skips all blanks and flags if there are more tokens.
*
* @author Bård Kvalheim
*****************************************************************************/

template <typename IsSeparator, typename IsPunctuation>
void
Fast_LatinTokenizer<IsSeparator, IsPunctuation>::SkipBlanks() {

    if (!_moreTokens) return;
    // Initialized with '\0' terminated buffer?
    if (_end == NULL) {
        while (*_next != '\0' && _isSeparator(*_next)) {
            ++_next;
        }
        if (*_next == '\0') {
            _moreTokens = false;
        }
    }

    // Initialized with specified buffer length.
    else {
        while (_next != _end && _isSeparator(*_next)) {
            ++_next;
        }
        if (_next == _end) {
            _moreTokens = false;
        }
    }

}

/**
*****************************************************************************
* Returns true if there are more tokens left in the text buffer.
*
* @author Bård Kvalheim
*****************************************************************************/

template <typename IsSeparator, typename IsPunctuation>
bool
Fast_LatinTokenizer<IsSeparator, IsPunctuation>::MoreTokens() {
    SkipBlanks();
    return _moreTokens;
}

/**
*****************************************************************************
* Returns the next token as a Fast_Token.
*
* @author Bård Kvalheim
*****************************************************************************/

template <typename IsSeparator, typename IsPunctuation>
typename Fast_LatinTokenizer<IsSeparator, IsPunctuation>::Fast_Token
Fast_LatinTokenizer<IsSeparator, IsPunctuation>::GetNextToken() {

    char *prev = _next;

    // Skip all blanks and flag if there are no more tokens.
    SkipBlanks();

    // Initialized with '\0' terminated buffer? Find the next blank or punctuation.
    if (_end == NULL) {
        while (*_next != '\0' && !_isSeparator(*_next) && !_isPunctuation(*_next)) {
            ++_next;
        }

        // Initialized with specified buffer length.
    }  else {
        while (_next != _end && !_isSeparator(*_next) && !_isPunctuation(*_next)) {
            ++_next;
        }
    }

    // Check if this token is a punctuation symbol, and generate token.
    bool isToken = ((_next - prev == 0) && _isPunctuation(*prev));

    if (isToken) {
        ++_next;
    }

    Fast_Token token(prev, _next, isToken);

    return token;

}

/**
*****************************************************************************
* Returns the original text buffer.
*
* @author Bård Kvalheim
*****************************************************************************/

template <typename IsSeparator, typename IsPunctuation>
char *
Fast_LatinTokenizer<IsSeparator, IsPunctuation>::GetOriginalText() {
    return _org;
}

/**
*****************************************************************************
* Helper class.
*
* When using isspace/1, ensure that the argument is cast to unsigned char to
* avoid problems with sign extension. See system documentation for details.
*
* @class   Fast_IsSpace
* @author Bård Kvalheim
* @date    Creation date: 2001-12-07
*****************************************************************************/

struct Fast_IsSpace {
    bool operator()(char c) {return (isspace(static_cast<unsigned char>(c)) != 0);}
};

/**
*****************************************************************************
* Helper class.
*
* When using ispunct/1, ensure that the argument is cast to unsigned char to
* avoid problems with sign extension. See system documentation for details.
*
* @class   Fast_IsPunctuation
* @author Bård Kvalheim
* @date    Creation date: 2001-12-07
*****************************************************************************/

struct Fast_IsPunctuation {
    bool operator()(char c) {return (ispunct(static_cast<unsigned char>(c)) != 0);}
};

/**
*****************************************************************************
* A simple tokenizer. See description above.
*
* @class   Fast_SimpleLatinTokenizer
* @author Bård Kvalheim
* @date    Creation date: 2001-12-07
*****************************************************************************/

typedef Fast_LatinTokenizer<Fast_IsSpace, Fast_IsPunctuation> Fast_SimpleLatinTokenizer;
