// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "xmlstream.hpp"
#include "string_escape.h"
#include <vespa/vespalib/encoding/base64.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <cassert>
#include <vector>

namespace vespalib::xml {

namespace {

    std::vector<bool> getLegalIdentifierFirstCharacters() {
        std::vector<bool> vec(256, false);
        for (uint32_t i='a'; i<='z'; ++i) vec[i] = true;
        for (uint32_t i='A'; i<='Z'; ++i) vec[i] = true;
        vec[':'] = true;
        vec['_'] = true;
        return vec;
    }

    std::vector<bool> getLegalIdentifierCharacters() {
        std::vector<bool> vec(getLegalIdentifierFirstCharacters());
        vec['-'] = true;
        vec['.'] = true;
        for (uint32_t i='0'; i<='9'; ++i) {
            vec[i] = true;
        }
        return vec;
    }

    std::vector<bool> getBinaryCharacters() {
        std::vector<bool> vec(256, false);
        for (uint32_t i=0; i<32; ++i) {
            vec[i] = true;
        }
        vec['\t'] = false;
        vec['\n'] = false;
        vec['\r'] = false;
        vec['\f'] = false;
        return vec;
    }

    std::vector<bool> legalIdentifierFirstChar(
            getLegalIdentifierFirstCharacters());
    std::vector<bool> legalIdentifierChars = getLegalIdentifierCharacters();
    std::vector<bool> binaryChars = getBinaryCharacters();

    bool containsBinaryCharacters(const std::string& s) {
        for (int i=0, n=s.size(); i<n; ++i) {
            if (binaryChars[static_cast<uint8_t>(s[i])]) return true;
        }
        return false;
    }

    void writeBase64Encoded(std::ostream& out, const std::string& s) {
        out << vespalib::Base64::encode(&s[0], s.size());
    }
}

bool isLegalName(const std::string& name) {
    if (name.size() == 0) return false;
    if (!legalIdentifierFirstChar[static_cast<uint8_t>(name[0])]) return false;
    for (int i=1, n=name.size(); i<n; ++i) {
        if (!legalIdentifierChars[static_cast<uint8_t>(name[i])]) return false;
    }
    return true;
}

void convertToLegalName(std::string& name) {
    if (name.size() == 0) {
        name = "__no_name__";
    } else {
        if (!legalIdentifierFirstChar[static_cast<uint8_t>(name[0])]) {
            name[0] = '_';
        }
        for (int i=1, n=name.size(); i<n; ++i) {
            if (!legalIdentifierChars[static_cast<uint8_t>(name[i])]) {
                name[i] = '_';
            }
        }
    }
}

XmlOutputStream::XmlOutputStream(std::ostream& ostream,
                                 const std::string& indent)
    : _indent(indent),
      _wrappedStream(ostream),
      _tagStack(),
      _cachedTag(),
      _cachedAttributes(),
      _cachedContent()
{
}

XmlAttribute::~XmlAttribute()
{
}

XmlContent::~XmlContent()
{
}

XmlOutputStream::~XmlOutputStream()
{
}

XmlOutputStream&
XmlOutputStream::operator<<(const XmlTag& tag)
{
    //std::cerr << "Trying to add tag " << tag.getName() << ". cached tag is "
    //          << (void*) _cachedTag.get() << "\n";
    if (_cachedTag.get() != 0) flush(false);
    _cachedTag.reset(new XmlTag(tag));
    _cachedContentType = XmlContent::AUTO;
    //std::cerr << "Added tag " << _cachedTag->getName() << "\n";
    return *this;
}

XmlOutputStream&
XmlOutputStream::operator<<(const XmlAttribute& attribute)
{
    //std::cerr << "Adding attribute\n";
    if (_cachedTag.get() == 0) {
        throw vespalib::IllegalStateException("Cannot add attribute "
                + attribute.getName() + ", as no tag is open");
    }
    _cachedAttributes.push_back(attribute);
    return *this;
}

XmlOutputStream&
XmlOutputStream::operator<<(const XmlEndTag&)
{
    //std::cerr << "Adding endtag\n";
    if (_cachedTag.get()) {
        flush(true);
        _cachedContentType = XmlContent::ESCAPED;
    } else if (_tagStack.empty()) {
        throw vespalib::IllegalStateException("No open tags left to end");
    } else {
        for (uint32_t i=1; i<_tagStack.size(); ++i) {
            _wrappedStream << _indent;
        }
        _wrappedStream << "</" << _tagStack.back() << ">";
        _tagStack.pop_back();
        if (!_tagStack.empty()) _wrappedStream << '\n';
        _cachedContentType = XmlContent::ESCAPED;
    }
    return *this;
}

XmlOutputStream&
XmlOutputStream::operator<<(const XmlContent& content)
{
    //std::cerr << "Adding content\n";
    if (_cachedTag.get() == 0 && _tagStack.empty()) {
        throw vespalib::IllegalStateException(
                "No open tag to write content in");
    }
    if (_cachedTag.get() != 0) {
        //std::cerr << "Content is '" << content.getContent() << "'\n";
        if (content.getType() == XmlContent::AUTO) { // Do nothing.. Always ok
        } else if (_cachedContentType == XmlContent::AUTO) {
            _cachedContentType = content.getType();
        } else if (_cachedContentType != content.getType()) {
            throw vespalib::IllegalStateException(
                "Have already added content of different type");
        }
        _cachedContent.push_back(content);
    } else {
        if (content.getType() == XmlContent::BASE64) {
            throw vespalib::IllegalStateException(
                "Cannot add Base64 encoded content after tag content");
        }
        for (uint32_t i=0; i<_tagStack.size(); ++i) {
            _wrappedStream << _indent;
        }
        _wrappedStream << content.getContent() << '\n';
    }
    return *this;
}

XmlOutputStream&
XmlOutputStream::operator<<(const XmlSerializable& serializable)
{
    //std::cerr << "Adding serializable\n";
    serializable.printXml(*this);
    return *this;
}

XmlOutputStream&
XmlOutputStream::operator<<(const std::string& content)
{
    //std::cerr << "Adding content string\n";
    return *this << XmlContent(content);
}

XmlOutputStream&
XmlOutputStream::operator<<(char c)
{
    return *this << XmlContent(std::string(&c, 1));
}

XmlOutputStream&
XmlOutputStream::operator<<(int32_t i)
{
    return *this << XmlContent(vespalib::make_string("%d", i));
}

XmlOutputStream&
XmlOutputStream::operator<<(int64_t i)
{
    return *this << XmlContent(vespalib::make_string("%" PRId64, i));
}

XmlOutputStream&
XmlOutputStream::operator<<(float f)
{
    return *this << XmlContent(vespalib::make_string("%g", f));
}

XmlOutputStream&
XmlOutputStream::operator<<(double d)
{
    return *this << XmlContent(vespalib::make_string("%g", d));
}

void
XmlOutputStream::flush(bool endTag)
{
    //std::cerr << "Flushing\n";
    if (_cachedTag.get() == 0) {
        throw vespalib::IllegalStateException("Cannot write non-existing tag");
    }
    for (uint32_t i=0; i<_tagStack.size(); ++i) {
        _wrappedStream << _indent;
    }
    _wrappedStream << '<' << _cachedTag->getName();
    for (std::list<XmlAttribute>::const_iterator it = _cachedAttributes.begin();
         it != _cachedAttributes.end(); ++it)
    {
        _wrappedStream << ' ' << it->getName() << "=\""
                       << xml_attribute_escaped(it->getValue()) << '"';
    }
    _cachedAttributes.clear();
    if (_cachedContent.empty() && endTag) {
        _wrappedStream << "/>\n";
    } else if (_cachedContent.empty()) {
        _wrappedStream << ">\n";
        _tagStack.push_back(_cachedTag->getName());
    } else {
        if (_cachedContentType == XmlContent::AUTO) {
            _cachedContentType = XmlContent::ESCAPED;
            for (std::list<XmlContent>::const_iterator it
                    = _cachedContent.begin(); it != _cachedContent.end(); ++it)
            {
                if (containsBinaryCharacters(it->getContent())) {
                    _cachedContentType = XmlContent::BASE64;
                    break;
                }
            }
        }
        if (_cachedContentType == XmlContent::BASE64) {
            _wrappedStream << " binaryencoding=\"base64\"";
        }
        _wrappedStream << '>';
        for (std::list<XmlContent>::const_iterator it = _cachedContent.begin();
             it != _cachedContent.end(); ++it)
        {
            if (!endTag) {
                _wrappedStream << '\n';
                for (uint32_t i=0; i<=_tagStack.size(); ++i) {
                    _wrappedStream << _indent;
                }
            }
            switch (_cachedContentType) {
                case XmlContent::ESCAPED: {
                    write_xml_content_escaped(_wrappedStream, it->getContent());
                    break;
                }
                case XmlContent::BASE64: {
                    writeBase64Encoded(_wrappedStream, it->getContent());
                    break;
                }
                default: assert(false);
            }
        }
        _cachedContent.clear();
        if (endTag) {
            _wrappedStream << "</" << _cachedTag->getName() << ">\n";
        } else {
            _wrappedStream << '\n';
            _tagStack.push_back(_cachedTag->getName());
        }
    }
    _cachedTag.reset(0);
}

XmlTag::XmlTag(const XmlTag& tag)
    : _name(tag._name),
      _attributes(),
      _content(),
      _flags(tag._flags)
{
}

XmlTag::~XmlTag() {}

XmlTag::XmlTag(const std::string& name, XmlTagFlags flags)
    : _name(name),
      _attributes(),
      _content(),
      _flags(flags)
{
    if (_flags == XmlTagFlags::CONVERT_ILLEGAL_CHARACTERS) {
        convertToLegalName(_name);
    }
    if (!isLegalName(_name)) {
        throw vespalib::IllegalArgumentException("Name '" + _name + "' contains "
                "illegal XML characters and cannot be used as tag name");
    }
}

XmlAttribute::XmlAttribute(const XmlAttribute& attribute)
    : _name(attribute._name),
      _value(attribute._value),
      _next()
{
}

XmlAttribute::XmlAttribute(const std::string& name, const char * value, uint32_t flags)
    : _name(name),
      _value(),
      _next()
{
    vespalib::asciistream ost;
    if (flags & HEX) ost << vespalib::hex << "0x";
    ost << value;
    _value = ost.str();
    if (!isLegalName(name)) {
        throw vespalib::IllegalArgumentException("Name '" + name + "' contains "
                "illegal XML characters and cannot be used as attribute name");
    }
}

XmlEndTag::XmlEndTag()
{
}

XmlContent::XmlContent(Type type)
    : _type(type),
      _content(),
      _nextContent(),
      _nextTag()
{
}

XmlContent::XmlContent()
    : _type(AUTO),
      _content(),
      _nextContent(),
      _nextTag()
{
}

XmlContent::XmlContent(const XmlContent& content)
    : _type(content._type),
      _content(content._content),
      _nextContent(),
      _nextTag()
{
}

XmlContent::XmlContent(const std::string& value)
    : _type(AUTO),
      _content(value),
      _nextContent(),
      _nextTag()
{
}

XmlContentWrapper::XmlContentWrapper(const XmlContentWrapper& wrapper)
    : XmlContent(wrapper)
{
}

XmlContentWrapper::XmlContentWrapper(const char* value)
    : XmlContent(std::string(value))
{
}

XmlContentWrapper::XmlContentWrapper(const char* value, uint32_t size)
    : XmlContent(std::string(value, size))
{
}

using CharP = char *;
using ConstCharP = const char *;

template XmlAttribute::XmlAttribute(const std::string &, std::string, unsigned int);
template XmlAttribute::XmlAttribute(const std::string &, vespalib::string, unsigned int);
template XmlAttribute::XmlAttribute(const std::string &, vespalib::stringref, unsigned int);
template XmlAttribute::XmlAttribute(const std::string &, CharP, unsigned int);
template XmlAttribute::XmlAttribute(const std::string &, bool, unsigned int);
template XmlAttribute::XmlAttribute(const std::string &, short, unsigned int);
template XmlAttribute::XmlAttribute(const std::string &, int, unsigned int);
template XmlAttribute::XmlAttribute(const std::string &, long, unsigned int);
template XmlAttribute::XmlAttribute(const std::string &, long long, unsigned int);
template XmlAttribute::XmlAttribute(const std::string &, unsigned short, unsigned int);
template XmlAttribute::XmlAttribute(const std::string &, unsigned int, unsigned int);
template XmlAttribute::XmlAttribute(const std::string &, unsigned long, unsigned int);
template XmlAttribute::XmlAttribute(const std::string &, unsigned long long, unsigned int);
template XmlAttribute::XmlAttribute(const std::string &, double, unsigned int);

}
