// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/xmlserializable.h>

namespace document {
    typedef vespalib::xml::XmlOutputStream XmlOutputStream;
    typedef vespalib::xml::XmlSerializable XmlSerializable;
    typedef vespalib::xml::XmlTag XmlTag;
    typedef vespalib::xml::XmlEndTag XmlEndTag;
    typedef vespalib::xml::XmlAttribute XmlAttribute;
    typedef vespalib::xml::XmlContent XmlContent;
    typedef vespalib::xml::XmlEscapedContent XmlEscapedContent;
    typedef vespalib::xml::XmlBase64Content XmlBase64Content;
    typedef vespalib::xml::XmlContentWrapper XmlContentWrapper;
}

