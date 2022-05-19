// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/xmlstream.h>

namespace vespalib {

class Test : public vespalib::TestApp
{
public:
    void testNormalUsage();
    void testEscaping();
    void testNesting();
    void testIndent();

    int Main() override;
};

int
Test::Main()
{
    TEST_INIT("xmlserializables_test");
    srandom(1);
    testNormalUsage();
    testEscaping();
    testNesting();
    testIndent();
    TEST_DONE();
}

void
Test::testNormalUsage()
{
    std::ostringstream ost;
    XmlOutputStream xos(ost);
    using namespace vespalib::xml;
    xos << XmlTag("car")
            << XmlTag("door")
                << XmlAttribute("windowstate", "up")
            << XmlEndTag()
            << XmlTag("description")
                << "This is a car description used to test"
            << XmlEndTag()
        << XmlEndTag();
    std::string expected =
        "<car>\n"
        "<door windowstate=\"up\"/>\n"
        "<description>This is a car description used to test</description>\n"
        "</car>";
    EXPECT_EQUAL(expected, ost.str());
}

void
Test::testEscaping()
{
    std::ostringstream ost;
    XmlOutputStream xos(ost);
    using namespace vespalib::xml;
    xos << XmlTag("!#trash%-", XmlTagFlags::CONVERT_ILLEGAL_CHARACTERS)
            << XmlTag("foo")
                << XmlAttribute("bar", "<100%\" &\n>")
            << XmlEndTag()
            << XmlTag("escaped")
                << XmlEscapedContent()
                << XmlContentWrapper("<>&\"'% \r\n\t\f\0", 12)
            << XmlEndTag()
            << XmlTag("encoded")
                << XmlBase64Content()
                << XmlContentWrapper("<>&\"'% \t\f\0", 10)
            << XmlEndTag()
            << XmlTag("auto1")
                << XmlContentWrapper("<>&\t\f\r\nfoo", 10)
            << XmlEndTag()
            << XmlTag("auto2")
                << XmlContentWrapper("<>&\t\0\r\nfoo", 10)
            << XmlEndTag()
        << XmlEndTag();
    std::string expected =
        "<__trash_->\n"
        "<foo bar=\"&lt;100%&quot; &amp;&#10;&gt;\"/>\n"
        "<escaped>&lt;&gt;&amp;\"'% &#13;\n&#9;&#12;&#0;</escaped>\n"
        "<encoded binaryencoding=\"base64\">PD4mIiclIAkMAA==</encoded>\n"
        "<auto1>&lt;&gt;&amp;&#9;&#12;&#13;\nfoo</auto1>\n"
        "<auto2 binaryencoding=\"base64\">PD4mCQANCmZvbw==</auto2>\n"
        "</__trash_->";
    EXPECT_EQUAL(expected, ost.str());
}

namespace {
    struct LookAndFeel : public XmlSerializable {

        LookAndFeel() {}

        void printXml(XmlOutputStream& out) const override {
            using namespace vespalib::xml;
            out << XmlAttribute("color", "blue")
                << XmlTag("other")
                    << XmlAttribute("count", 5)
                    << XmlTag("something") << "foo" << XmlEndTag()
                    << XmlTag("else") << "bar" << XmlEndTag()
                << XmlEndTag();
        }
    };
}

void
Test::testNesting()
{
    std::ostringstream ost;
    XmlOutputStream xos(ost);
    using namespace vespalib::xml;
    xos << XmlTag("car")
            << XmlTag("door")
                << LookAndFeel()
            << XmlEndTag()
            << XmlTag("description")
                << "This is a car description used to test"
            << XmlEndTag()
        << XmlEndTag();
    std::string expected =
        "<car>\n"
        "<door color=\"blue\">\n"
        "<other count=\"5\">\n"
        "<something>foo</something>\n"
        "<else>bar</else>\n"
        "</other>\n"
        "</door>\n"
        "<description>This is a car description used to test</description>\n"
        "</car>";
    EXPECT_EQUAL(expected, ost.str());
}

void
Test::testIndent()
{
    std::ostringstream ost;
    XmlOutputStream xos(ost, "  ");
    using namespace vespalib::xml;
    xos << XmlTag("foo")
            << XmlTag("bar") << 2.14 << XmlEndTag()
            << "Litt innhold"
            << XmlTag("nytag")
                << "Mer innhold"
                << XmlTag("base")
                    << XmlBase64Content() << "foobar"
                << XmlEndTag()
            << XmlEndTag()
        << XmlEndTag();
    std::string expected =
        "<foo>\n"
        "  <bar>2.14</bar>\n"
        "  Litt innhold\n"
        "  <nytag>\n"
        "    Mer innhold\n"
        "    <base binaryencoding=\"base64\">Zm9vYmFy</base>\n"
        "  </nytag>\n"
        "</foo>";
    EXPECT_EQUAL(expected, ost.str());
}

} // vespalib

TEST_APPHOOK(vespalib::Test)
