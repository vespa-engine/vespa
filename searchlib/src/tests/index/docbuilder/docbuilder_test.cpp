// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/log.h>
LOG_SETUP("docbuilder_test");
#include <boost/algorithm/string/classification.hpp>
#include <boost/algorithm/string/split.hpp>
#include <vespa/searchlib/index/docbuilder.h>
#include <vespa/vespalib/encoding/base64.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/document/repo/fixedtyperepo.h>
#include <iostream>

using namespace document;
using search::index::schema::CollectionType;

namespace search {
namespace index {

namespace
{
std::string empty;
}

namespace linguistics
{
const vespalib::string SPANTREE_NAME("linguistics");
}

class Test : public vespalib::TestApp {
private:
    void testBuilder();
public:
    int Main() override;
};

void
Test::testBuilder()
{
    Schema s;
    s.addIndexField(Schema::IndexField("ia", schema::DataType::STRING));
    s.addIndexField(Schema::IndexField("ib", schema::DataType::STRING, CollectionType::ARRAY));
    s.addIndexField(Schema::IndexField("ic", schema::DataType::STRING, CollectionType::WEIGHTEDSET));
    s.addUriIndexFields(Schema::IndexField("iu", schema::DataType::STRING));
    s.addUriIndexFields(Schema::IndexField("iau", schema::DataType::STRING, CollectionType::ARRAY));
    s.addUriIndexFields(Schema::IndexField("iwu", schema::DataType::STRING, CollectionType::WEIGHTEDSET));
    s.addAttributeField(Schema::AttributeField("aa", schema::DataType::INT32));
    s.addAttributeField(Schema::AttributeField("ab", schema::DataType::FLOAT));
    s.addAttributeField(Schema::AttributeField("ac", schema::DataType::STRING));
    s.addAttributeField(Schema::AttributeField("ad", schema::DataType::INT32, CollectionType::ARRAY));
    s.addAttributeField(Schema::AttributeField("ae", schema::DataType::FLOAT, CollectionType::ARRAY));
    s.addAttributeField(Schema::AttributeField("af", schema::DataType::STRING, CollectionType::ARRAY));
    s.addAttributeField(Schema::AttributeField("ag", schema::DataType::INT32, CollectionType::WEIGHTEDSET));
    s.addAttributeField(Schema::AttributeField("ah", schema::DataType::FLOAT, CollectionType::WEIGHTEDSET));
    s.addAttributeField(Schema::AttributeField("ai", schema::DataType::STRING, CollectionType::WEIGHTEDSET));
    s.addAttributeField(Schema::AttributeField("asp1", schema::DataType::INT32));
    s.addAttributeField(Schema::AttributeField("asp2", schema::DataType::INT64));
    s.addAttributeField(Schema::AttributeField("aap1", schema::DataType::INT32, CollectionType::ARRAY));
    s.addAttributeField(Schema::AttributeField("aap2", schema::DataType::INT64, CollectionType::ARRAY));
    s.addAttributeField(Schema::AttributeField("awp1", schema::DataType::INT32, CollectionType::WEIGHTEDSET));
    s.addAttributeField(Schema::AttributeField("awp2", schema::DataType::INT64, CollectionType::WEIGHTEDSET));

    s.addSummaryField(Schema::SummaryField("sa", schema::DataType::INT8));
    s.addSummaryField(Schema::SummaryField("sb", schema::DataType::INT16));
    s.addSummaryField(Schema::SummaryField("sc", schema::DataType::INT32));
    s.addSummaryField(Schema::SummaryField("sd", schema::DataType::INT64));
    s.addSummaryField(Schema::SummaryField("se", schema::DataType::FLOAT));
    s.addSummaryField(Schema::SummaryField("sf", schema::DataType::DOUBLE));
    s.addSummaryField(Schema::SummaryField("sg", schema::DataType::STRING));
    s.addSummaryField(Schema::SummaryField("sh", schema::DataType::RAW));
    s.addSummaryField(Schema::SummaryField("si", schema::DataType::RAW, CollectionType::ARRAY));
    s.addSummaryField(Schema::SummaryField("sj", schema::DataType::RAW, CollectionType::WEIGHTEDSET));

    DocBuilder b(s);
    Document::UP doc;
    std::vector<std::string> lines;
    std::vector<std::string>::const_iterator itr;
    std::string xml;

    { // empty
        doc = b.startDocument("id:ns:searchdocument::0").endDocument();
        xml = doc->toXml("");
        boost::split(lines, xml, boost::is_any_of("\n"));
        itr = lines.begin();
        EXPECT_EQUAL("<document documenttype=\"searchdocument\" documentid=\"id:ns:searchdocument::0\"/>", *itr++);
        EXPECT_EQUAL("", *itr++);
        EXPECT_TRUE(itr == lines.end());
    }
    { // all fields set
        std::vector<char> binaryBlob;
        binaryBlob.push_back('\0');
        binaryBlob.push_back('\2');
        binaryBlob.push_back('\1');
        std::string raw1s("Single Raw Element");
        std::string raw1a0("Array Raw Element 0");
        std::string raw1a1("Array Raw Element  1");
        std::string raw1w0("Weighted Set Raw Element 0");
        std::string raw1w1("Weighted Set Raw Element  1");
        raw1s += std::string(&binaryBlob[0],
                             &binaryBlob[0] + binaryBlob.size());
        raw1a0 += std::string(&binaryBlob[0],
                              &binaryBlob[0] + binaryBlob.size());
        raw1a1 += std::string(&binaryBlob[0],
                              &binaryBlob[0] + binaryBlob.size());
        raw1w0 += std::string(&binaryBlob[0],
                              &binaryBlob[0] + binaryBlob.size());
        raw1w1 += std::string(&binaryBlob[0],
                              &binaryBlob[0] + binaryBlob.size());
        b.startDocument("id:ns:searchdocument::1");
        b.startIndexField("ia").addStr("foo").addStr("bar").addStr("baz").addTermAnnotation("altbaz").endField();
        b.startIndexField("ib").startElement().addStr("foo").endElement().
            startElement(1).addStr("bar").addStr("baz").endElement().endField();
        b.  startIndexField("ic").
            startElement(20).addStr("bar").addStr("baz").endElement().
            startElement().addStr("foo").endElement().
            endField();
        b.startIndexField("iu").
            startSubField("all").
            addUrlTokenizedString("http://www.example.com:81/fluke?ab=2#4").
            endSubField().
            startSubField("scheme").
            addUrlTokenizedString("http").
            endSubField().
            startSubField("host").
            addUrlTokenizedString("www.example.com").
            endSubField().
            startSubField("port").
            addUrlTokenizedString("81").
            endSubField().
            startSubField("path").
            addUrlTokenizedString("/fluke").
            endSubField().
            startSubField("query").
            addUrlTokenizedString("ab=2").
            endSubField().
            startSubField("fragment").
            addUrlTokenizedString("4").
            endSubField().
            endField();
        b.startIndexField("iau").
            startElement(1).
            startSubField("all").
            addUrlTokenizedString("http://www.example.com:82/fluke?ab=2#8").
            endSubField().
            startSubField("scheme").
            addUrlTokenizedString("http").
            endSubField().
            startSubField("host").
            addUrlTokenizedString("www.example.com").
            endSubField().
            startSubField("port").
            addUrlTokenizedString("82").
            endSubField().
            startSubField("path").
            addUrlTokenizedString("/fluke").
            endSubField().
            startSubField("query").
            addUrlTokenizedString("ab=2").
            endSubField().
            startSubField("fragment").
            addUrlTokenizedString("8").
            endSubField().
            endElement().
            startElement(1).
            startSubField("all").
            addUrlTokenizedString("http://www.flickr.com:82/fluke?ab=2#9").
            endSubField().
            startSubField("scheme").
            addUrlTokenizedString("http").
            endSubField().
            startSubField("host").
            addUrlTokenizedString("www.flickr.com").
            endSubField().
            startSubField("port").
            addUrlTokenizedString("82").
            endSubField().
            startSubField("path").
            addUrlTokenizedString("/fluke").
            endSubField().
            startSubField("query").
            addUrlTokenizedString("ab=2").
            endSubField().
            startSubField("fragment").
            addUrlTokenizedString("9").
            endSubField().
            endElement().
            endField();
        b.startIndexField("iwu").
            startElement(4).
            startSubField("all").
            addUrlTokenizedString("http://www.example.com:83/fluke?ab=2#12").
            endSubField().
            startSubField("scheme").
            addUrlTokenizedString("http").
            endSubField().
            startSubField("host").
            addUrlTokenizedString("www.example.com").
            endSubField().
            startSubField("port").
            addUrlTokenizedString("83").
            endSubField().
            startSubField("path").
            addUrlTokenizedString("/fluke").
            endSubField().
            startSubField("query").
            addUrlTokenizedString("ab=2").
            endSubField().
            startSubField("fragment").
            addUrlTokenizedString("12").
            endSubField().
            endElement().
            startElement(7).
            startSubField("all").
            addUrlTokenizedString("http://www.flickr.com:85/fluke?ab=2#13").
            endSubField().
            startSubField("scheme").
            addUrlTokenizedString("http").
            endSubField().
            startSubField("host").
            addUrlTokenizedString("www.flickr.com").
            endSubField().
            startSubField("port").
            addUrlTokenizedString("85").
            endSubField().
            startSubField("path").
            addUrlTokenizedString("/fluke").
            endSubField().
            startSubField("query").
            addUrlTokenizedString("ab=2").
            endSubField().
            startSubField("fragment").
            addUrlTokenizedString("13").
            endSubField().
            endElement().
            endField();
        b.startAttributeField("aa").addInt(2147483647).endField();
        b.startAttributeField("ab").addFloat(1234.56).endField();
        b.startAttributeField("ac").addStr("foo baz").endField();
        b.startAttributeField("ad").startElement().addInt(10).endElement().endField();
        b.startAttributeField("ae").startElement().addFloat(10.5).endElement().endField();
        b.startAttributeField("af").startElement().addStr("foo").endElement().endField();
        b.startAttributeField("ag").startElement(2).addInt(20).endElement().endField();
        b.startAttributeField("ah").startElement(3).addFloat(20.5).endElement().endField();
        b.startAttributeField("ai").startElement(4).addStr("bar").endElement().endField();
        b.startAttributeField("asp1").addInt(1001).endField();
        b.startAttributeField("asp2").addPosition(1002, 1003).endField();
        b.startAttributeField("aap1").
            startElement().addInt(1004).endElement().
            startElement().addInt(1005).endElement().
            endField();
        b.startAttributeField("aap2").
            startElement().addPosition(1006, 1007).endElement().
            startElement().addPosition(1008, 1009).endElement().
            endField();
        b.startAttributeField("awp1").
            startElement(41).addInt(1010).endElement().
            startElement(42).addInt(1011).endElement().
            endField();
        b.startAttributeField("awp2").
            startElement(43).addPosition(1012, 1013).endElement().
            startElement(44).addPosition(1014, 1015).endElement().
            endField();
        b.startSummaryField("sa").addInt(127).endField();
        b.startSummaryField("sb").addInt(32767).endField();
        b.startSummaryField("sc").addInt(2147483647).endField();
        b.startSummaryField("sd").addInt(2147483648).endField();
        b.startSummaryField("se").addFloat(1234.56).endField();
        b.startSummaryField("sf").addFloat(9876.54).endField();
        b.startSummaryField("sg").addStr("foo bar").endField();
        b.startSummaryField("sh").
            addRaw(raw1s.c_str(), raw1s.size()).
            endField();
        b.startSummaryField("si").
            startElement().
            addRaw(raw1a0.c_str(), raw1a0.size()).
            endElement().
            startElement().
            addRaw(raw1a1.c_str(), raw1a1.size()).
            endElement().
            endField();
        b.startSummaryField("sj").
            startElement(46).
            addRaw(raw1w1.c_str(), raw1w1.size()).
            endElement().
            startElement(45).
            addRaw(raw1w0.c_str(), raw1w0.size()).
            endElement().
            endField();
        doc = b.endDocument();
        xml = doc->toXml("");
        boost::split(lines, xml, boost::is_any_of("\n"));
        itr = lines.begin();
        EXPECT_EQUAL("<document documenttype=\"searchdocument\" documentid=\"id:ns:searchdocument::1\">", *itr++);
        EXPECT_EQUAL("<sj>", *itr++);
        EXPECT_EQUAL(empty +"<item weight=\"46\" binaryencoding=\"base64\">" +
                   vespalib::Base64::encode(raw1w1) +
                   "</item>", *itr++);
        EXPECT_EQUAL(empty + "<item weight=\"45\" binaryencoding=\"base64\">" +
                   vespalib::Base64::encode(raw1w0) +
                   "</item>", *itr++);
        EXPECT_EQUAL("</sj>", *itr++);
        EXPECT_EQUAL("<sa>127</sa>", *itr++);
        EXPECT_EQUAL("<iu>", *itr++);
        EXPECT_EQUAL("<all>http://www.example.com:81/fluke?ab=2#4</all>", *itr++);
        EXPECT_EQUAL("<host>www.example.com</host>", *itr++);
        EXPECT_EQUAL("<scheme>http</scheme>", *itr++);
        EXPECT_EQUAL("<path>/fluke</path>", *itr++);
        EXPECT_EQUAL("<port>81</port>", *itr++);
        EXPECT_EQUAL("<query>ab=2</query>", *itr++);
        EXPECT_EQUAL("<fragment>4</fragment>", *itr++);
        EXPECT_EQUAL("</iu>", *itr++);
        EXPECT_EQUAL("<sf>9876.54</sf>", *itr++);
        EXPECT_EQUAL("<aa>2147483647</aa>", *itr++);
        EXPECT_EQUAL("<aap2>", *itr++);
        EXPECT_EQUAL("<item>1047806</item>", *itr++);
        EXPECT_EQUAL("<item>1048322</item>", *itr++);
        EXPECT_EQUAL("</aap2>", *itr++);
        EXPECT_EQUAL("<se>1234.56</se>", *itr++);
        EXPECT_EQUAL("<sg>foo bar</sg>", *itr++);
        EXPECT_EQUAL("<ia>foo bar baz</ia>", *itr++);
        EXPECT_EQUAL("<si>", *itr++);
        EXPECT_EQUAL(empty + "<item binaryencoding=\"base64\">" +
                   vespalib::Base64::encode(raw1a0) +
                   "</item>", *itr++);
        EXPECT_EQUAL(empty + "<item binaryencoding=\"base64\">" +
                   vespalib::Base64::encode(raw1a1) +
                   "</item>", *itr++);
        EXPECT_EQUAL("</si>", *itr++);
        EXPECT_EQUAL("<ae>", *itr++);
        EXPECT_EQUAL("<item>10.5</item>", *itr++);
        EXPECT_EQUAL("</ae>", *itr++);
        EXPECT_EQUAL("<ib>", *itr++);
        EXPECT_EQUAL("<item>foo</item>", *itr++);
        EXPECT_EQUAL("<item>bar baz</item>", *itr++);
        EXPECT_EQUAL("</ib>", *itr++);
        EXPECT_EQUAL("<sd>2147483648</sd>", *itr++);
        EXPECT_EQUAL("<ah>", *itr++);
        EXPECT_EQUAL("<item weight=\"3\">20.5</item>", *itr++);
        EXPECT_EQUAL("</ah>", *itr++);
        EXPECT_EQUAL("<sb>32767</sb>", *itr++);
        EXPECT_EQUAL("<ic>", *itr++);
        EXPECT_EQUAL("<item weight=\"20\">bar baz</item>", *itr++);
        EXPECT_EQUAL("<item weight=\"1\">foo</item>", *itr++);
        EXPECT_EQUAL("</ic>", *itr++);
        EXPECT_EQUAL("<ac>foo baz</ac>", *itr++);
        EXPECT_EQUAL("<awp2>", *itr++);
        EXPECT_EQUAL("<item weight=\"43\">1048370</item>", *itr++);
        EXPECT_EQUAL("<item weight=\"44\">1048382</item>", *itr++);
        EXPECT_EQUAL("</awp2>", *itr++);
        EXPECT_EQUAL("<iau>", *itr++);
        EXPECT_EQUAL("<item>", *itr++);
        EXPECT_EQUAL("<all>http://www.example.com:82/fluke?ab=2#8</all>", *itr++);
        EXPECT_EQUAL("<host>www.example.com</host>", *itr++);
        EXPECT_EQUAL("<scheme>http</scheme>", *itr++);
        EXPECT_EQUAL("<path>/fluke</path>", *itr++);
        EXPECT_EQUAL("<port>82</port>", *itr++);
        EXPECT_EQUAL("<query>ab=2</query>", *itr++);
        EXPECT_EQUAL("<fragment>8</fragment>", *itr++);
        EXPECT_EQUAL("</item>", *itr++);
        EXPECT_EQUAL("<item>", *itr++);
        EXPECT_EQUAL("<all>http://www.flickr.com:82/fluke?ab=2#9</all>", *itr++);
        EXPECT_EQUAL("<host>www.flickr.com</host>", *itr++);
        EXPECT_EQUAL("<scheme>http</scheme>", *itr++);
        EXPECT_EQUAL("<path>/fluke</path>", *itr++);
        EXPECT_EQUAL("<port>82</port>", *itr++);
        EXPECT_EQUAL("<query>ab=2</query>", *itr++);
        EXPECT_EQUAL("<fragment>9</fragment>", *itr++);
        EXPECT_EQUAL("</item>", *itr++);
        EXPECT_EQUAL("</iau>", *itr++);
        EXPECT_EQUAL("<asp2>1047758</asp2>", *itr++);
        EXPECT_EQUAL("<sc>2147483647</sc>", *itr++);
        EXPECT_EQUAL("<ai>", *itr++);
        EXPECT_EQUAL("<item weight=\"4\">bar</item>", *itr++);
        EXPECT_EQUAL("</ai>", *itr++);
        EXPECT_EQUAL("<asp1>1001</asp1>", *itr++);
        EXPECT_EQUAL("<ad>", *itr++);
        EXPECT_EQUAL("<item>10</item>", *itr++);
        EXPECT_EQUAL("</ad>", *itr++);
        EXPECT_EQUAL("<iwu>", *itr++);
        EXPECT_EQUAL("<item weight=\"4\">", *itr++);
        EXPECT_EQUAL("<all>http://www.example.com:83/fluke?ab=2#12</all>", *itr++);
        EXPECT_EQUAL("<host>www.example.com</host>", *itr++);
        EXPECT_EQUAL("<scheme>http</scheme>", *itr++);
        EXPECT_EQUAL("<path>/fluke</path>", *itr++);
        EXPECT_EQUAL("<port>83</port>", *itr++);
        EXPECT_EQUAL("<query>ab=2</query>", *itr++);
        EXPECT_EQUAL("<fragment>12</fragment>", *itr++);
        EXPECT_EQUAL("</item>", *itr++);
        EXPECT_EQUAL("<item weight=\"7\">", *itr++);
        EXPECT_EQUAL("<all>http://www.flickr.com:85/fluke?ab=2#13</all>", *itr++);
        EXPECT_EQUAL("<host>www.flickr.com</host>", *itr++);
        EXPECT_EQUAL("<scheme>http</scheme>", *itr++);
        EXPECT_EQUAL("<path>/fluke</path>", *itr++);
        EXPECT_EQUAL("<port>85</port>", *itr++);
        EXPECT_EQUAL("<query>ab=2</query>", *itr++);
        EXPECT_EQUAL("<fragment>13</fragment>", *itr++);
        EXPECT_EQUAL("</item>", *itr++);
        EXPECT_EQUAL("</iwu>", *itr++);
        EXPECT_EQUAL("<ab>1234.56</ab>", *itr++);
        EXPECT_EQUAL("<ag>", *itr++);
        EXPECT_EQUAL("<item weight=\"2\">20</item>", *itr++);
        EXPECT_EQUAL("</ag>", *itr++);
        EXPECT_EQUAL("<awp1>", *itr++);
        EXPECT_EQUAL("<item weight=\"41\">1010</item>", *itr++);
        EXPECT_EQUAL("<item weight=\"42\">1011</item>", *itr++);
        EXPECT_EQUAL("</awp1>", *itr++);
        EXPECT_EQUAL("<aap1>", *itr++);
        EXPECT_EQUAL("<item>1004</item>", *itr++);
        EXPECT_EQUAL("<item>1005</item>", *itr++);
        EXPECT_EQUAL("</aap1>", *itr++);
        EXPECT_EQUAL(empty + "<sh binaryencoding=\"base64\">" +
                   vespalib::Base64::encode(raw1s) +
                   "</sh>", *itr++);
        EXPECT_EQUAL("<af>", *itr++);
        EXPECT_EQUAL("<item>foo</item>", *itr++);
        EXPECT_EQUAL("</af>", *itr++);
        EXPECT_EQUAL("</document>", *itr++);
        EXPECT_TRUE(itr == lines.end());
#if 1
        std::cout << "onedoc xml start -----" << std::endl <<
            xml << std::endl <<
            "-------" << std::endl;
        std::cout << "onedoc toString start ----" << std::endl <<
            doc->toString(true) << std::endl <<
            "-------" << std::endl;
#endif
    }
    { // create one more to see that everything is cleared
        b.startDocument("id:ns:searchdocument::2");
        b.startIndexField("ia").addStr("yes").endField();
        b.startAttributeField("aa").addInt(20).endField();
        b.startSummaryField("sa").addInt(10).endField();
        doc = b.endDocument();
        xml = doc->toXml("");
        boost::split(lines, xml, boost::is_any_of("\n"));
        itr = lines.begin();
        EXPECT_EQUAL("<document documenttype=\"searchdocument\" documentid=\"id:ns:searchdocument::2\">", *itr++);
        EXPECT_EQUAL("<sa>10</sa>", *itr++);
        EXPECT_EQUAL("<aa>20</aa>", *itr++);
        EXPECT_EQUAL("<ia>yes</ia>", *itr++);
        EXPECT_EQUAL("</document>", *itr++);
        EXPECT_TRUE(itr == lines.end());
    }
    { // create field with cjk chars
        b.startDocument("id:ns:searchdocument::3");
        b.startIndexField("ia").
            addStr("我就是那个").
            setAutoSpace(false).
            addStr("大灰狼").
            setAutoSpace(true).
            endField();
        doc = b.endDocument();
        xml = doc->toXml("");
        boost::split(lines, xml, boost::is_any_of("\n"));
        itr = lines.begin();
        EXPECT_EQUAL("<document documenttype=\"searchdocument\" documentid=\"id:ns:searchdocument::3\">", *itr++);
        EXPECT_EQUAL("<ia>我就是那个大灰狼</ia>", *itr++);
        EXPECT_EQUAL("</document>", *itr++);
        EXPECT_TRUE(itr == lines.end());
        const FieldValue::UP iaval = doc->getValue("ia");
        ASSERT_TRUE(iaval.get() != NULL);
        const StringFieldValue *iasval = dynamic_cast<const StringFieldValue *>
                                         (iaval.get());
        ASSERT_TRUE(iasval != NULL);
        StringFieldValue::SpanTrees trees = iasval->getSpanTrees();
        const SpanTree *tree = StringFieldValue::findTree(trees, linguistics::SPANTREE_NAME);
        ASSERT_TRUE(tree != NULL);
        std::vector<Span> spans;
        std::vector<Span> expSpans;
        for (SpanTree::const_iterator i = tree->begin(), ie = tree->end();
             i != ie; ++i) {
            Annotation &ann = const_cast<Annotation &>(*i);
            const Span *span = dynamic_cast<const Span *>(ann.getSpanNode());
            if (span == NULL)
                continue;
            spans.push_back(*span);
        }
        expSpans.push_back(Span(0, 15));
        expSpans.push_back(Span(0, 15));
        expSpans.push_back(Span(15, 9));
        expSpans.push_back(Span(15, 9));
        ASSERT_TRUE(expSpans == spans);
#if 1
        std::cout << "onedoc xml start -----" << std::endl <<
            xml << std::endl <<
            "-------" << std::endl;
        std::cout << "onedoc toString start ----" << std::endl <<
            doc->toString(true) << std::endl <<
            "-------" << std::endl;
#endif
    }
}

int
Test::Main()
{
    TEST_INIT("docbuilder_test");

    testBuilder();

    TEST_DONE();
}

}
}

TEST_APPHOOK(search::index::Test);

