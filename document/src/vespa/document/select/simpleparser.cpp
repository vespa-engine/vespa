// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "simpleparser.h"
#include "compare.h"
#include <cerrno>

namespace document::select::simple {

size_t eatWhite(const char * s, size_t len)
{
    size_t pos(0);
    for (;(pos < len) && isspace(s[pos]); pos++);
    return pos;
}

bool icmp(char c, char l)
{
    return tolower(c) == l;
}

bool IdSpecParser::parse(vespalib::stringref s)
{
    bool retval(false);
    size_t pos(eatWhite(s.data(), s.size()));
    if (pos+1 < s.size()) {
        if (icmp(s[pos], 'i') && icmp(s[pos+1],'d')) {
            pos += 2;
            if (pos < s.size()) {
                switch (s[pos]) {
                case '.':
                    {
                        int widthBits(-1);
                        int divisionBits(-1);
                        size_t startPos(++pos);
                        for (;(pos < s.size()) && (tolower(s[pos]) >= 'a') && (tolower(s[pos]) <= 'z'); pos++);
                        size_t len(pos - startPos);
                        if (((len == 4) && (strncasecmp(&s[startPos], "user", 4) == 0 ||
                                            strncasecmp(&s[startPos], "type", 4) == 0)) ||
                            ((len == 5) && (strncasecmp(&s[startPos], "group", 5) == 0)) ||
                            ((len == 6) && (strncasecmp(&s[startPos], "scheme", 6) == 0)) ||
                            ((len == 8) && (strncasecmp(&s[startPos], "specific", 8) == 0)) ||
                            ((len == 9) && (strncasecmp(&s[startPos], "namespace", 9) == 0)))
                        {
                            retval = true;
                            setValue(ValueNode::UP(new IdValueNode(_bucketIdFactory, "id", s.substr(startPos, len), widthBits, divisionBits)));
                        } else {
                            pos = startPos;
                        }
                    }
                    break;
                case '!':
                case '<':
                case '>':
                case '=':
                case '\t':
                case '\n':
                case '\r':
                case ' ':
                    {
                        retval = true;
                        setValue(ValueNode::UP(new IdValueNode(_bucketIdFactory, "id", "")));
                    }
                    break;
                default:
                    break;
                }
            }
        }
    }
    setRemaining(s.substr(pos));
    return retval;
}

bool OperatorParser::parse(vespalib::stringref s)
{
    bool retval(false);
    size_t pos(eatWhite(s.data(), s.size()));
    if (pos+1 < s.size()) {
        retval = true;
        if (s[pos] == '=') {
            pos++;
            if (s[pos] == '=') {
                pos++;
                _operator = & Operator::get("==");
            } else if (s[pos] == '~') {
                pos++;
                _operator = & Operator::get("=~");
            } else {
                _operator = & Operator::get("=");
            }
        } else if (s[pos] == '>') {
            pos++;
            if (s[pos] == '=') {
                _operator = & Operator::get(">=");
                pos++;
            } else {
                _operator = & Operator::get(">");
            }
        } else if (s[pos] == '<') {
            pos++;
            if (s[pos] == '=') {
                pos++;
                _operator = & Operator::get("<=");
            } else {
                _operator = & Operator::get("<");
            }
        } else if ((s[pos] == '!') && (s[pos] == '=')) {
            pos += 2;
            _operator = & Operator::get("!=");
        } else {
            retval = false;
        }
    }
    setRemaining(s.substr(pos));
    return retval;
}

bool StringParser::parse(vespalib::stringref s)
{
    bool retval(false);
    setRemaining(s);
    size_t pos(eatWhite(s.data(), s.size()));
    if (pos + 1 < s.size()) {
        if (s[pos++] == '"') {
            vespalib::string str;
            for(;(pos < s.size()) && (s[pos] != '"');pos++) {
                if ((pos < s.size()) && (s[pos] == '\\')) {
                    pos++;
                }
                str += s[pos];
            }
            if (s[pos] == '"') {
                pos++;
                retval = true;
                setValue(ValueNode::UP(new StringValueNode(str)));
            }
        }
        setRemaining(s.substr(pos+1));
    }
    return retval;
}

bool IntegerParser::parse(vespalib::stringref s)
{
    bool retval(false);
    size_t pos(eatWhite(s.data(), s.size()));
    if (pos < s.size()) {
        char * err(NULL);
        errno = 0;
        bool isHex((s.size() - pos) && (s[pos] == '0') && (s[pos+1] == 'x'));
        int64_t v = isHex
                    ? strtoul(&s[pos], &err, 0)
                    : strtol(&s[pos], &err, 0);
        long len = err - &s[pos];
        if ((errno == 0) && (pos+len <= s.size())) {
            retval = true;
            pos += len;
            setValue(ValueNode::UP(new IntegerValueNode(v, false)));
        }
    }
    setRemaining(s.substr(pos));
    return retval;
}

bool SelectionParser::parse(vespalib::stringref s)
{
    bool retval(false);
    IdSpecParser id(_bucketIdFactory);
    if (id.parse(s)) {
        OperatorParser op;
        if (op.parse(id.getRemaining())) {
            if (id.isUserSpec()) {
                IntegerParser v;
                if (v.parse(op.getRemaining())) {
                    setNode(Node::UP(new Compare(id.stealValue(), *op.getOperator(), v.stealValue(), _bucketIdFactory)));
                    retval = true;
                }
                setRemaining(v.getRemaining());
            } else {
                StringParser v;
                if (v.parse(op.getRemaining())) {
                    setNode(Node::UP(new Compare(id.stealValue(), *op.getOperator(), v.stealValue(), _bucketIdFactory)));
                    retval = true;
                }
                setRemaining(v.getRemaining());
            }
        } else {
            setRemaining(op.getRemaining());
        }
    } else {
        setRemaining(id.getRemaining());
    }
    return retval;
}

}
