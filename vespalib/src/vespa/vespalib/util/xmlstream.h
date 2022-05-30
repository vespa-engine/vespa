// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @file xmlserializable.h
 * @ingroup util
 *
 * @brief Interfaces to be used for XML serialization.
 *
 * This file contains XML utility classes, to make XML serialization simple.
 * Rather than users writing their own XML, these tools let you define a tree
 * structure, and this library builds the XML for you. This ensures that you
 * write legal XML and that stuff that needs to be escaped is.
 * <p>
 * It defines a superclass for XML serializable classes, called XmlSerializable.
 * This is what classes that should be XML serializable will inherit.
 * <p>
 * When implementing the printXml() function in XmlSerializable, one will
 * use the various XML helper classes defined here to build a tree structure
 * creating the XML. These are: XmlTag, XmlEndTag, XmlAttribute, and XmlContent.
 * Some subclasses exist of XmlContent to facilitate various types of content.
 * <p>
 * The XmlOutputStream wraps a regular std::ostream. You write XML objects to it
 * and it is responsible for writing all the XML code. This way, the XML
 * serialization is done without interfering with regular output operators.
 * <p>
 * For example usage, refer to the unit test:
 * vespalib/tests/xmlserializable/xmlserializabletest.cpp
 *
 */

#pragma once

#include "xmlserializable.h"
#include <iosfwd>
#include <list>
#include <memory>

namespace vespalib::xml {

class XmlAttribute;
class XmlContent;
class XmlOutputStream;

bool isLegalName(const std::string& name);

enum class XmlTagFlags { NONE = 0, CONVERT_ILLEGAL_CHARACTERS = 1 };

/**
 * @class document::XmlTag
 *
 * @brief Start a new tag with given name.
 */
class XmlTag {
    std::string _name;
    std::unique_ptr<XmlAttribute> _attributes;
    std::unique_ptr<XmlContent> _content;
    XmlTagFlags _flags;
public:
    XmlTag(const XmlTag&);
    XmlTag(const std::string& name, XmlTagFlags = XmlTagFlags::NONE);
    ~XmlTag();

    const std::string& getName() const { return _name; }
};

/**
 * @class document::XmlEndTag
 *
 * @brief Indicates that current tag is closed.
 */
class XmlEndTag {
public:
    XmlEndTag();
};

/**
 * @class document::XmlAttribute
 *
 * @brief Defined a single attribute within an XML tag.
 *
 * When adding an XML to an XML stream, the attribute will be added to the last
 * tag added. This can not be called after the last tag opened in the stream is
 * closed, so add all attributes before starting to add new XML child tags.
 */
class XmlAttribute {
    std::string _name;
    std::string _value;
    std::unique_ptr<XmlAttribute> _next;
public:
    enum Flag { NONE = 0x0, HEX = 0x1 };
    XmlAttribute(const XmlAttribute&);
    /** Add any value that can be written to an ostringstream. */
    template<typename T>
    XmlAttribute(const std::string& name, T value, uint32_t flags = NONE);
    XmlAttribute(const std::string& name, const char * value, uint32_t flags = NONE);
    ~XmlAttribute();

    const std::string& getName() const { return _name; }
    const std::string& getValue() const { return _value; }
};


/**
 * @class document::XmlContent
 *
 * XML content to be written to stream. By default it will autodetect whether to
 * escape or base64 encode content. XmlOutputStream functions taking primitives
 * will generate XmlContent instances.
 */
class XmlContent {
public:
    enum Type { AUTO, ESCAPED, BASE64 };
protected:
    XmlContent(Type type);
private:
    Type _type;
    std::string _content;
    std::unique_ptr<XmlContent> _nextContent;
    std::unique_ptr<XmlTag> _nextTag;

public:
    XmlContent();
    XmlContent(const XmlContent&);
    XmlContent(const std::string& value);
    ~XmlContent();

    Type getType() const { return _type; }
    const std::string& getContent() const { return _content; }
};

/**
 * @class document::XmlEscapedContent
 *
 * Token used to tell that this content field should only be XML escaped.
 */
class XmlEscapedContent : public XmlContent {
public:
    XmlEscapedContent() : XmlContent(ESCAPED) {}
};

/**
 * @class document::XmlBase64Content
 *
 * Token used to tell that this content field should always be base64 encoded.
 */
class XmlBase64Content : public XmlContent {
public:
    XmlBase64Content() : XmlContent(BASE64) {}
};

/**
 * @class document::XmlContentWrapper
 *
 * A wrapper class for content that one doesn't want to copy or release
 * ownership of. This wrapper merely takes pointer to data, and assumes it
 * will stay alive as long as needed.
 */
class XmlContentWrapper : public XmlContent {
public:
    XmlContentWrapper(const XmlContentWrapper&);
    XmlContentWrapper(const char* value);
    XmlContentWrapper(const char* value, uint32_t size);
};

/**
 * @class document::XmlOutputStream
 *
 * @brief std::ostream wrapper, only accepting data that will become XML.
 *
 * After XmlEndTag() has been sent to the stream, the tag is guarantueed to have
 * been written. Call isFinalized() to ensure that you have closed all the tags
 * that have been opened. Within a tag, the stream will cache some information,
 * as more information might be required before knowing what to print.
 */
class XmlOutputStream {
    const std::string _indent;
    std::ostream& _wrappedStream;
    std::list<std::string> _tagStack;
    std::unique_ptr<XmlTag> _cachedTag;
    std::list<XmlAttribute> _cachedAttributes;
    std::list<XmlContent> _cachedContent;
    XmlContent::Type _cachedContentType;

    void flush(bool endTag);

public:

    XmlOutputStream(std::ostream& ostream, const std::string& indent = "");
    ~XmlOutputStream();

    bool isFinalized() const
        { return (_tagStack.empty() && _cachedTag.get() == 0); }

    std::ostream& getWrappedStream() { return _wrappedStream; }

    XmlOutputStream& operator<<(const XmlTag& tag);
    XmlOutputStream& operator<<(const XmlAttribute& attribute);
    XmlOutputStream& operator<<(const XmlEndTag& endtag);
    XmlOutputStream& operator<<(const XmlContent& content);
    XmlOutputStream& operator<<(const XmlSerializable& serializable);

    XmlOutputStream& operator<<(const std::string& content);
    XmlOutputStream& operator<<(char c);
    XmlOutputStream& operator<<(int32_t i);
    XmlOutputStream& operator<<(int64_t i);
    XmlOutputStream& operator<<(float f);
    XmlOutputStream& operator<<(double d);
};

}

