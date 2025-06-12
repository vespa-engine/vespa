// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "testenv.h"
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/testkit/test_path.h>
#include <map>
#include <cctype>
#include <vespa/fastos/file.h>
#include <vespa/juniper/juniper_separators.h>

#include <vespa/log/log.h>
LOG_SETUP(".auxtest");

// Using separator definitions only from here:

#define COLOR_HIGH_ON  "\e[1;31m"
#define COLOR_HIGH_OFF "\e[0m"

static int debug_level = 0;

bool                 color_highlight = false;
bool                 verbose = false;
const unsigned char* connectors = reinterpret_cast<const unsigned char*>("-'");

SummaryConfig* _sumconf = nullptr;

using juniper::SpecialTokenRegistry;

int countBrokenUTF8(const char* data, uint32_t len) {
    int broken = 0;
    int remain = 0;

    for (uint32_t i = 0; i < len; ++i) {
        unsigned char val = data[i];
        switch (val & 0xc0) {
        case 0xc0: // first char
            remain = 1;
            val <<= 2;
            while ((val & 0x80) != 0) {
                ++remain;
                val <<= 1;
            }
            if (remain > 5) {
                ++broken;
                remain = 0;
            }
            break;
        case 0x80: // continuation char
            if (remain == 0) {
                ++broken;
            } else {
                --remain;
            }
            break;
        default: // single char
            if (remain > 0) {
                ++broken;
                remain = 0;
            }
            break;
        }
    }
    return broken;
}

TEST(AuxTest, testDoubleWidth) {
    char input[17] = "[\x1f\xef\xbd\x93\xef\xbd\x8f\xef\xbd\x8e\xef\xbd\x99\x1f]";

    juniper::PropertyMap myprops;
    myprops // no fallback, should get match
        .set("juniper.dynsum.escape_markup", "off")
        .set("juniper.dynsum.highlight_off", "</hi>")
        .set("juniper.dynsum.continuation", "<sep />")
        .set("juniper.dynsum.highlight_on", "<hi>");
    Fast_NormalizeWordFolder wf;
    juniper::Juniper         juniper(&myprops, &wf);
    juniper::Config          myConfig("best", juniper);

    juniper::QueryParser q("\xef\xbd\x93\xef\xbd\x8f\xef\xbd\x8e\xef\xbd\x99");
    juniper::QueryHandle qh(q, nullptr);
    auto                 res = juniper::Analyse(myConfig, qh, input, 17, 0);
    EXPECT_TRUE(static_cast<bool>(res));

    juniper::Summary* sum = juniper::GetTeaser(*res, nullptr);
    (void)sum;
    // this should work
    // EXPECT_TRUE(sum->Length() != 0);
}

TEST(AuxTest, testPartialUTF8) {
    const int inputSize = 5769; // NB: update this if input is changed
    char      input[inputSize];
    {
        FastOS_File file(TEST_PATH("partialutf8.input.utf8").c_str());
        EXPECT_TRUE(file.OpenReadOnly());
        EXPECT_TRUE(file.getSize() == inputSize);
        EXPECT_TRUE(file.Read(input, inputSize));
        EXPECT_TRUE(countBrokenUTF8(input, inputSize) == 0);
    }

    juniper::PropertyMap myprops;
    myprops // config taken from vespa test case
        .set("juniper.dynsum.escape_markup", "off")
        .set("juniper.dynsum.highlight_off", "\x1F")
        .set("juniper.dynsum.continuation", "")
        .set("juniper.dynsum.fallback", "prefix")
        .set("juniper.dynsum.highlight_on", "\x1F");
    Fast_NormalizeWordFolder wf;
    juniper::Juniper         juniper(&myprops, &wf);
    juniper::Config          myConfig("best", juniper);

    juniper::QueryParser q("ipod");
    juniper::QueryHandle qh(q, nullptr);
    auto                 res = juniper::Analyse(myConfig, qh, input, inputSize, 0);
    EXPECT_TRUE(static_cast<bool>(res));

    juniper::Summary* sum = juniper::GetTeaser(*res, nullptr);
    EXPECT_TRUE(sum->Length() != 0);

    // check for partial/broken utf-8
    EXPECT_TRUE(countBrokenUTF8(sum->Text(), sum->Length()) == 0);
}

TEST(AuxTest, testLargeBlockChinese) {
    const int inputSize = 10410; // NB: update this if input is changed
    char      input[inputSize];
    {
        FastOS_File file(TEST_PATH("largeblockchinese.input.utf8").c_str());
        EXPECT_TRUE(file.OpenReadOnly());
        EXPECT_TRUE(file.getSize() == inputSize);
        EXPECT_TRUE(file.Read(input, inputSize));
        EXPECT_TRUE(countBrokenUTF8(input, inputSize) == 0);
    }

    juniper::PropertyMap myprops;
    myprops // config taken from reported bug
        .set("juniper.dynsum.length", "50")
        .set("juniper.dynsum.min_length", "20")
        .set("juniper.dynsum.escape_markup", "off")
        .set("juniper.dynsum.highlight_off", "\x1F")
        .set("juniper.dynsum.continuation", "")
        .set("juniper.dynsum.fallback", "prefix")
        .set("juniper.dynsum.highlight_on", "\x1F");
    Fast_NormalizeWordFolder wf;
    juniper::Juniper         juniper(&myprops, &wf);
    juniper::Config          myConfig("best", juniper);

    juniper::QueryParser q("希望");
    juniper::QueryHandle qh(q, nullptr);
    auto                 res = juniper::Analyse(myConfig, qh, input, inputSize, 0);
    EXPECT_TRUE(static_cast<bool>(res));

    juniper::Summary* sum = juniper::GetTeaser(*res, nullptr);
    EXPECT_TRUE(sum->Length() != 0);

    // check that the entire block of chinese data is not returned in the summary
    EXPECT_TRUE(sum->Length() < 100);

    // check for partial/broken utf-8
    EXPECT_TRUE(countBrokenUTF8(sum->Text(), sum->Length()) == 0);
}

TEST(AuxTest, testExample) {
    juniper::QueryParser q("AND(consume,sleep,tree)");
    juniper::QueryHandle qh(q, nullptr);

    // some content
    const char* content = "the monkey consumes bananas and sleeps afterwards."
                          "&%#%&! cries the sleepy monkey and jumps down from the tree."
                          "the last token here is split across lines consumed";
    int         content_len = strlen(content);
    auto        res = juniper::Analyse(*juniper::TestConfig, qh, content, content_len, 0);
    EXPECT_TRUE(static_cast<bool>(res));

    res->Scan();
    Matcher& m = *res->_matcher;
    EXPECT_TRUE(m.TotalMatchCnt(0) == 2 && m.ExactMatchCnt(0) == 0);
}

TEST(AuxTest, testPropertyMap) {
    juniper::PropertyMap map;
    IJuniperProperties*  props = &map;
    map.set("foo", "bar").set("one", "two");
    EXPECT_TRUE(props->GetProperty("bogus") == nullptr);
    EXPECT_TRUE(strcmp(props->GetProperty("bogus", "default"), "default") == 0);
    EXPECT_TRUE(strcmp(props->GetProperty("foo"), "bar") == 0);
    EXPECT_TRUE(strcmp(props->GetProperty("one", "default"), "two") == 0);
}

TEST(AuxTest, testRerase) {
    std::list<int> ls;

    for (int i = 0; i < 10; i++) ls.push_back(i);

    for (std::list<int>::reverse_iterator rit = ls.rbegin(); rit != ls.rend();) {
        if (*rit == 5 || *rit == 6) {
            // STL hackers heaven - puh this was cumbersome..
            std::list<int>::reverse_iterator new_it(ls.erase((++rit).base()));
            rit = new_it;
        } else
            ++rit;
    }

    std::string s;
    for (std::list<int>::iterator it = ls.begin(); it != ls.end(); ++it) s += ('0' + *it);
    EXPECT_TRUE(s == std::string("01234789"));
}

// Debug dump with positions for reference
void test_dump(const char* s, unsigned int len) {
    printf("test_dump: length %u\n", len);
    for (unsigned int i = 0; i < len;) {
        unsigned int start = i;
        for (; i < len;) {
            if ((signed char)s[i] < 0) {
                printf("�");
            } else {
                printf("%c", s[i]);
            }
            i++;
            if (!(i % 100)) break;
        }
        printf("\n");
        i = start + 10;
        for (; i < len && i % 100; i += 10) printf("%7s%3d", "", i);
        printf("\n");
    }
}

namespace {

#if defined(__cpp_char8_t)
const char* char_from_u8(const char8_t* p) {
    return reinterpret_cast<const char*>(p);
}
#else
const char* char_from_u8(const char* p) {
    return p;
}
#endif

} // namespace

void TestUTF8(unsigned int size) {
    const char* s = char_from_u8(u8"\u00e5pent s\u00f8k\u00e6\u00f8\u00e5\u00e6\u00f8\u00e5\u00e6\u00f8\u00e5");
    const unsigned char* p = (const unsigned char*)s;

    int moved = 0;
    for (int i = 0; i < (int)size + 2; i++) {
        // Forward tests:
        p = (const unsigned char*)(s + i);
        moved = Fast_UnicodeUtil::UTF8move((const unsigned char*)s, size, p, +1);
        LOG(spam, "forw. moved %d, pos %d", moved, i);
        if (i == 0 || i == 8)
            EXPECT_TRUE(moved == 2);
        else if (i >= (int)size)
            EXPECT_TRUE(moved == -1);
        else
            EXPECT_TRUE(moved == 1);

        // backward tests
        p = (const unsigned char*)(s + i);
        moved = Fast_UnicodeUtil::UTF8move((const unsigned char*)s, size, p, -1);
        LOG(spam, "backw.moved %d, pos %d", moved, i);
        if (i == 10 || i == 9 || i == 2)
            EXPECT_TRUE(moved == 2);
        else if (i == 0 || i > (int)size)
            EXPECT_TRUE(moved == -1);
        else
            EXPECT_TRUE(moved == 1);

        // move-to-start tests:
        p = (const unsigned char*)(s + i);
        moved = Fast_UnicodeUtil::UTF8move((const unsigned char*)s, size, p, 0);
        LOG(spam, "to-start.moved %d, pos %d", moved, i);
        if (i == 9 || i == 1)
            EXPECT_TRUE(moved == 1);
        else if (i >= (int)size)
            EXPECT_TRUE(moved == -1);
        else
            EXPECT_TRUE(moved == 0);
    }

    // Assumption about equality of UCS4 IsWordChar and isalnum for
    // ascii (c < 128) :
    for (unsigned char c = 0; c < 128; c++) {
        const unsigned char* pc = &c;
        ucs4_t               u = Fast_UnicodeUtil::GetUTF8Char(pc);
        bool                 utf8res = Fast_UnicodeUtil::IsWordChar(u);
        bool                 asciires = std::isalnum(c);
        EXPECT_TRUE(utf8res == asciires);
        if (utf8res != asciires) fprintf(stderr, ":%c:%d != :%c:%d\n", u, utf8res, c, asciires);
    }
}

TEST(AuxTest, testUTF811) {
    SCOPED_TRACE("11");
    TestUTF8(11);
}

TEST(AuxTest, testUTF812) {
    SCOPED_TRACE("12");
    TestUTF8(12);
}

void test_summary(Matcher& m, const char* content, size_t content_len, int size, int matches,
                           int surround, size_t& charsize) {
    SummaryDesc* sum = m.CreateSummaryDesc(size, size, matches, surround);
    EXPECT_TRUE(sum != nullptr);
    if (!sum) {
        // No summary generated!
        return;
    }
    std::string res = BuildSummary(content, content_len, sum, _sumconf, charsize);

    if ((verbose || ::testing::Test::HasFailure()) && debug_level > 0) {
        printf("\nRequested size: %d, matches: %d, surround: %d, Summary size %lu :%s:\n", size, matches, surround,
               static_cast<unsigned long>(res.size()), res.c_str());
    }
    DeleteSummaryDesc(sum);
}

TEST(AuxTest, testUTF8context) {
    const char*          iso_cont = char_from_u8(u8"AND(m\u00b5ss,fast,s\u00f8kemotor,\u00e5relang)");
    juniper::QueryParser q(iso_cont);
    juniper::QueryHandle qh(q, nullptr);

    // some content
    std::string s(
        char_from_u8(u8"Fast leverer s\u00d8kemotorer og andre nyttige ting for \u00e5 finne frem p\u00e5 "));
    s.append(char_from_u8(u8"internett. Teknologien er basert p\u00e5 \u00c5relang"));
    s += juniper::separators::unit_separator_string;
    s.append(char_from_u8(u8"norsk innsats og forskning i"));
    s += juniper::separators::group_separator_string;
    s.append(
        char_from_u8(u8"trondheimsmilj\u00f8et. M\u00b5ss med denne nye funksjonaliteten for \u00e5 vise frem"));
    s += juniper::separators::unit_separator_string;
    s.append(
        char_from_u8(u8" beste forekomst av s\u00f8ket med s\u00f8kemotor til brukeren blir det enda bedre. "));
    s.append(char_from_u8(u8"Hvis bare UTF8-kodingen virker som den skal for tegn som tar mer enn \u00e9n byte."));

    auto res = juniper::Analyse(*juniper::TestConfig, qh, s.c_str(), s.size(), 0);
    EXPECT_TRUE(static_cast<bool>(res));

    size_t   charsize;
    Matcher& m = *res->_matcher;

    res->Scan();
    EXPECT_TRUE(m.TotalMatchCnt(0) == 1 && m.ExactMatchCnt(0) == 1);
    EXPECT_TRUE(m.TotalMatchCnt(1) == 1 && m.ExactMatchCnt(2) == 1);
    EXPECT_TRUE(m.TotalMatchCnt(2) == 2 && m.ExactMatchCnt(2) == 1);
    EXPECT_TRUE(m.TotalMatchCnt(3) == 1 && m.ExactMatchCnt(2) == 1);

    char separators[3];
    separators[0] = juniper::separators::unit_separator;
    separators[1] = juniper::separators::group_separator;
    separators[2] = '\0';

    if (color_highlight)
        _sumconf = CreateSummaryConfig(COLOR_HIGH_ON, COLOR_HIGH_OFF, "...", separators, connectors);
    else
        _sumconf = CreateSummaryConfig("<hit>", "</hit>", "...", separators, connectors);
    for (int i = 1; i <= 10; i++) {
        // Short summaries with many matches
        test_summary(m, s.c_str(), s.size(), i * 30, i / 3, i * 10, charsize);
        // fewer matches, longer summaries
        test_summary(m, s.c_str(), s.size(), i * 60, i / 6, i * 20, charsize);
    }
    // Summary som er stort nok til � ta hele teksten
    test_summary(m, s.c_str(), s.size(), 800, 100, 300, charsize);
    // fprintf(stderr, "charsize %d s.size %d\n", charsize, s.size());
    EXPECT_TRUE(charsize == s.size() - 3 - 11); // Subtract eliminated separators and dual bytes

    // "Syke" settinger for summary:
    test_summary(m, s.c_str(), s.size(), 10000, 0, 1000, charsize);
    // fprintf(stderr, "charsize %d s.size %d\n", charsize, s.size());
    EXPECT_TRUE(charsize == s.size() - 3 - 11); // Subtract eliminated separators and dual bytes

    if (::testing::Test::HasFailure() && debug_level > 0) {
        fprintf(stderr, "Characters in original text: %ld\n", s.size());
        test_dump(s.c_str(), s.size());
        m.dump_statistics();
    }
    DeleteSummaryConfig(_sumconf);
}

struct TermTextPair {
    const char* term;
    const char* text;
};

static TermTextPair testjap[] = {
    // japanese string as term
    {"私はガラスを食べられます",
     "this is some japanese: 私はガラスを食べられます。それは私を傷つけません。 ending here"},

    // HUGE japanese prefix and postfix and simple match in middle:
    {"bond",
     "私はガラスを食べられます。それは私を傷つけません。私はガラスを食べられます。それは私を傷つけません。私はガラ"
     "スを食べられます。それは私を傷つけません。私はガラスを食べられます。それは私を傷つけません。私はガラスを食べ"
     "られます。それは私を傷つけません。私はガラスを食べられます。それは私を傷つけません。私はガラスを食べられます"
     "。それは私を傷つけません。私はガラスを食べられます。それは私を傷つけません。私はガラスを食べられます。それは"
     "私を傷つけません。私はガラスを食べられます。それは私を傷つけません。私はガラスを食べられます。それは私を傷つ"
     "けません。私はガラスを食べられます。それは私を傷つけません。 bond "
     "私はガラスを食べられます。それは私を傷つけません。私はガラスを食べられます。それは私を傷つけません。私はガラ"
     "スを食べられます。それは私を傷つけません。私はガラスを食べられます。それは私を傷つけません。私はガラスを食べ"
     "られます。それは私を傷つけません。私はガラスを食べられます。それは私を傷つけません。私はガラスを食べられます"
     "。それは私を傷つけません。私はガラスを食べられます。それは私を傷つけません。私はガラスを食べられます。それは"
     "私を傷つけません。私はガラスを食べられます。それは私を傷つけません。私はガラスを食べられます。それは私を傷つ"
     "けません。私はガラスを食べられます。それは私を傷つけません。"},
    {"japanese", "Simple。match。check。for。japanese。sep"},
    {"hit", " -. hit at start"},
    {"hit", "hit at end .,: "},
    {"hit",
     "------------------------------------------------------------------------------------------------------------"
     "---------this is a text that is long enough to generate a hit that does have dots on both sides "
     ";;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;"
     ";;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; "},
    {nullptr, nullptr}};

TEST(AuxTest, testJapanese) {
    for (int i = 0; testjap[i].term != nullptr; i++) {
        const char*          qstr = testjap[i].term;
        juniper::QueryParser q(qstr);
        juniper::QueryHandle qh(q, nullptr);

        const char* content = testjap[i].text;
        int         content_len = strlen(content);
        auto        res = juniper::Analyse(*juniper::TestConfig, qh, content, content_len, 0);
        EXPECT_TRUE(static_cast<bool>(res));

        size_t   charsize;
        Matcher& m = *res->_matcher;

        res->Scan();
        if (color_highlight)
            _sumconf = CreateSummaryConfig(COLOR_HIGH_ON, COLOR_HIGH_OFF, "...", "", connectors);
        else
            _sumconf = CreateSummaryConfig("<hit>", "</hit>", "...", "", connectors);

        SummaryDesc* sumdesc = m.CreateSummaryDesc(256, 256, 4, 80);
        EXPECT_TRUE(sumdesc != nullptr);
        if (!sumdesc) return;
        std::string sum = BuildSummary(content, content_len, sumdesc, _sumconf, charsize);

        switch (i) {
        case 0:
            // Matching a multibyte sequence
            EXPECT_TRUE(m.TotalMatchCnt(0) == 1 && m.ExactMatchCnt(0) == 1);
            // printf("total %d exact %d\n", m.TotalMatchCnt(0),m.ExactMatchCnt(0));
            break;
        case 1:
            // Matching short word in loong multibyte sequence
            EXPECT_TRUE(m.TotalMatchCnt(0) == 1 && m.ExactMatchCnt(0) == 1);
            EXPECT_TRUE(sum.size() <= 400);
            break;
        case 2:
            // Matching word in between multibyte separators
            EXPECT_TRUE(m.TotalMatchCnt(0) == 1 && m.ExactMatchCnt(0) == 1);
            break;
        case 3:
            // Check that result is the complete string (markup excluded)
            EXPECT_TRUE(sum.size() - 11 == charsize);
            // printf("sz %d charsz %d :%s:\n", sum.size(), charsize, sum.c_str());
            break;
        case 4:
            // Check that result is the complete string (markup excluded)
            EXPECT_TRUE(sum.size() - 11 == charsize);
            // printf("sz %d charsz %d :%s:\n", sum.size(), charsize, sum.c_str());
            break;
        case 5:
            // Check that we get no noise at the start or end of this
            EXPECT_TRUE(sum.size() == 103 && charsize == 86);
            // printf("sz %d charsz %d :%s:\n", sum.size(), charsize, sum.c_str());
            break;
        default: break;
        }
        DeleteSummaryDesc(sumdesc);
        DeleteSummaryConfig(_sumconf);
    }
}

TEST(AuxTest, testStartHits) {
    juniper::QueryParser q("elvis");
    juniper::QueryHandle qh(q, "dynlength.120");

    const char* content = "Elvis, this is a long match before matching Elvis again and then som more text at"
                          " the end. But this text at the end must be much longer than this to trigger the case."
                          " In fact it must be much longer. And then som more text at the end. But this text at "
                          "the end must be much longer than this to trigger the case";
    int         content_len = strlen(content);
    auto        res = juniper::Analyse(*juniper::TestConfig, qh, content, content_len, 0);
    EXPECT_TRUE(static_cast<bool>(res));

    juniper::Summary* sum = juniper::GetTeaser(*res, nullptr);
    (void)sum;
    // TODO: ReEnable    EXPECT_TRUE(sum->Length() != 0);
}

TEST(AuxTest, testEndHit) {
    juniper::QueryParser q("match");
    juniper::QueryHandle qh(q, "dynlength.120");

    const char* content = "In this case we need a fairly long text that does not fit entirely into the resulting"
                          " summary, but that has a hit towards the end of the document where the expected length"
                          " extends the end of the doc. This means that the prefix must be more than 256 bytes"
                          " long. Here is the stuff we are looking for to match in a case where we have "
                          "surround_len bytes closer than good towardstheend�����������������������������������";
    size_t      content_len = strlen(content) - 55;

    auto res = juniper::Analyse(*juniper::TestConfig, qh, content, content_len, 0);
    EXPECT_TRUE(static_cast<bool>(res));

    juniper::Summary* sum = juniper::GetTeaser(*res, nullptr);
    EXPECT_TRUE(sum->Length() != 0);
}

TEST(AuxTest, testJuniperStack) {
    // Stack simplification tests
    QueryExpr* q = new QueryNode(1, 0, 0);
    QueryExpr* q1 = new QueryNode(1, 0, 0);
    QueryExpr* q2 = new QueryTerm("Hepp", 0, 100);
    q->AddChild(q1);
    q1->AddChild(q2);

    SimplifyStack(q);

    std::string s;
    q->Dump(s);
    EXPECT_TRUE(strcmp(s.c_str(), "Hepp:100") == 0);
    delete q;

    if (::testing::Test::HasFailure()) fprintf(stderr, "TestJuniperStack: %s\n", s.c_str());

    q = new QueryNode(2, 0, 0);
    q->_arity = 0;
    SimplifyStack(q);
    std::string s1;
    EXPECT_TRUE(q == nullptr);

    if (::testing::Test::HasFailure()) fprintf(stderr, "TestJuniperStack: %s\n", s.c_str());
}

class TokenProcessor : public ITokenProcessor {
private:
    const std::string&       _text;
    std::vector<std::string> _tokens;

public:
    TokenProcessor(const std::string& text) : _text(text), _tokens() {}
    void handle_token(Token& t) override {
        _tokens.push_back(std::string(_text.c_str() + t.bytepos, t.bytelen));
        // LOG(info, "handle_token(%s): bytepos(%d), wordpos(%d), bytelen(%d), curlen(%d)",
        //_tokens.back().c_str(),
        //(int)t.bytepos, (int)t.wordpos, t.bytelen, t.curlen);
    }
    void handle_end(Token& t) override {
        _tokens.push_back(std::string(_text.c_str() + t.bytepos, t.bytelen));
        // LOG(info, "handle_end(%s): bytepos(%d), wordpos(%d), bytelen(%d), curlen(%d)",
        //_tokens.back().c_str(),
        //(int)t.bytepos, (int)t.wordpos, t.bytelen, t.curlen);
    }
    const std::vector<std::string>& getTokens() const { return _tokens; }
};

bool assertChar(ucs4_t act, char exp) {
    // LOG(info, "assertChar(%d(%c), %c)", act, (char)act, exp);
    EXPECT_TRUE((char)act == exp);
    return ((char)act == exp);
}

using QueryNodeUP = std::unique_ptr<QueryNode>;
struct QB {
    QueryNodeUP q;
    QB(size_t numTerms) : q(new QueryNode(numTerms, 0, 0)) {}
    QB(QB& rhs) : q(std::move(rhs.q)) {}
    QB& add(const char* t, bool st = true) {
        QueryTerm* qt = new QueryTerm(t, 0, 100);
        if (st) qt->_options |= X_SPECIALTOKEN;
        q->AddChild(qt);
        return *this;
    }
};
struct Ctx {
    std::string              text;
    QB                       qb;
    SpecialTokenRegistry     str;
    Fast_NormalizeWordFolder wf;
    TokenProcessor           tp;
    JuniperTokenizer         jt;
    Ctx(const std::string& text_, QB& qb_);
    ~Ctx();
};

Ctx::Ctx(const std::string& text_, QB& qb_)
  : text(text_),
    qb(qb_),
    str(qb.q.get()),
    wf(),
    tp(text),
    jt(&wf, text.c_str(), text.size(), &tp, &str) {
    jt.scan();
}
Ctx::~Ctx() = default;

TEST(AuxTest, testSpecialTokenRegistry) {
    {
        using CharStream = SpecialTokenRegistry::CharStream;
        ucs4_t buf[16];
        {
            std::string text = " c+-";
            CharStream  cs(text.c_str(), text.c_str() + text.size(), buf, buf + 16);
            EXPECT_TRUE(!cs.isStartWordChar());
            EXPECT_TRUE(cs.hasMoreChars());
            EXPECT_TRUE(assertChar(cs.getNextChar(), ' '));
            EXPECT_TRUE(cs.hasMoreChars());
            cs.reset();
            EXPECT_TRUE(cs.hasMoreChars());
            EXPECT_TRUE(assertChar(cs.getNextChar(), ' '));
            EXPECT_TRUE(assertChar(cs.getNextChar(), 'c'));
            EXPECT_TRUE(cs.hasMoreChars());
            cs.reset();
            EXPECT_TRUE(cs.hasMoreChars());
            EXPECT_TRUE(assertChar(cs.getNextChar(), ' '));
            EXPECT_TRUE(assertChar(cs.getNextChar(), 'c'));
            EXPECT_TRUE(assertChar(cs.getNextChar(), '+'));
            EXPECT_TRUE(cs.hasMoreChars());
            cs.reset();
            EXPECT_TRUE(cs.hasMoreChars());
            EXPECT_TRUE(assertChar(cs.getNextChar(), ' '));
            EXPECT_TRUE(assertChar(cs.getNextChar(), 'c'));
            EXPECT_TRUE(assertChar(cs.getNextChar(), '+'));
            EXPECT_TRUE(assertChar(cs.getNextChar(), '-'));
            EXPECT_TRUE(!cs.hasMoreChars());
            cs.reset();
            EXPECT_TRUE(cs.hasMoreChars());
            EXPECT_TRUE(assertChar(cs.getNextChar(), ' '));
            EXPECT_TRUE(cs.hasMoreChars());
            EXPECT_TRUE(assertChar(cs.getNextChar(), 'c'));
            EXPECT_TRUE(cs.hasMoreChars());
            EXPECT_TRUE(assertChar(cs.getNextChar(), '+'));
            EXPECT_TRUE(cs.hasMoreChars());
            EXPECT_TRUE(assertChar(cs.getNextChar(), '-'));
            EXPECT_TRUE(!cs.hasMoreChars());
        }
        { // test reset with increase to next char
            std::string text = " c+-";
            CharStream  cs(text.c_str(), text.c_str() + text.size(), buf, buf + 16);
            EXPECT_TRUE(cs.resetAndInc());
            EXPECT_TRUE(cs.isStartWordChar());
            EXPECT_TRUE(cs.hasMoreChars());
            EXPECT_TRUE(assertChar(cs.getNextChar(), 'c'));
            EXPECT_TRUE(assertChar(cs.getNextChar(), '+'));
            EXPECT_TRUE(assertChar(cs.getNextChar(), '-'));
            EXPECT_TRUE(!cs.hasMoreChars());
            cs.reset();
            EXPECT_TRUE(cs.hasMoreChars());
            EXPECT_TRUE(assertChar(cs.getNextChar(), 'c'));
            EXPECT_TRUE(assertChar(cs.getNextChar(), '+'));
            EXPECT_TRUE(assertChar(cs.getNextChar(), '-'));
            EXPECT_TRUE(!cs.hasMoreChars());
            EXPECT_TRUE(cs.resetAndInc());
            EXPECT_TRUE(!cs.isStartWordChar());
            EXPECT_TRUE(cs.hasMoreChars());
            EXPECT_TRUE(assertChar(cs.getNextChar(), '+'));
            EXPECT_TRUE(assertChar(cs.getNextChar(), '-'));
            EXPECT_TRUE(!cs.hasMoreChars());
            EXPECT_TRUE(cs.resetAndInc());
            EXPECT_TRUE(!cs.isStartWordChar());
            EXPECT_TRUE(cs.hasMoreChars());
            EXPECT_TRUE(assertChar(cs.getNextChar(), '-'));
            EXPECT_TRUE(!cs.hasMoreChars());
            EXPECT_TRUE(!cs.resetAndInc());
            EXPECT_TRUE(!cs.hasMoreChars());
        }
        { // test lower case
            std::string text = "C";
            CharStream  cs(text.c_str(), text.c_str() + text.size(), buf, buf + 16);
            EXPECT_TRUE(assertChar(cs.getNextChar(), 'c'));
        }
    }
    { // test tokenizer with special token registry

        { // only special token registered
            Ctx c("foo", QB(2).add("c++").add("foo", false));
            EXPECT_TRUE(c.str.getSpecialTokens().size() == 1);
        }
        { // various matches
            std::string annotation = "\357\277\271dvdplusminus\357\277\272dvd+-\357\277\273";
            std::string text = "c++ !my C++ text ?.net dvd+- stuff " + annotation;
            Ctx         c(text, QB(3).add("c++").add(".net").add("dvd+-", false));
            EXPECT_TRUE(c.str.getSpecialTokens().size() == 2);
            EXPECT_TRUE(c.tp.getTokens().size() == 9);
            EXPECT_TRUE(c.tp.getTokens()[0] == "c++");
            EXPECT_TRUE(c.tp.getTokens()[1] == "my");
            EXPECT_TRUE(c.tp.getTokens()[2] == "C++");
            EXPECT_TRUE(c.tp.getTokens()[3] == "text");
            EXPECT_TRUE(c.tp.getTokens()[4] == ".net");
            EXPECT_TRUE(c.tp.getTokens()[5] == "dvd");
            EXPECT_TRUE(c.tp.getTokens()[6] == "stuff");
            EXPECT_TRUE(c.tp.getTokens()[7] == annotation);
            EXPECT_TRUE(c.tp.getTokens()[8] == "");
        }
        { // cannot start inside a word
            Ctx c("foo ac++", QB(1).add("c++"));
            EXPECT_TRUE(c.tp.getTokens().size() == 3);
            EXPECT_TRUE(c.tp.getTokens()[0] == "foo");
            EXPECT_TRUE(c.tp.getTokens()[1] == "ac");
            EXPECT_TRUE(c.tp.getTokens()[2] == "");
        }
        { // can end inside a word (TODO: can be fixed if it is a problem)
            Ctx c("++ca foo", QB(1).add("++c"));
            EXPECT_TRUE(c.tp.getTokens().size() == 4);
            EXPECT_TRUE(c.tp.getTokens()[0] == "++c");
            EXPECT_TRUE(c.tp.getTokens()[1] == "a");
            EXPECT_TRUE(c.tp.getTokens()[2] == "foo");
            EXPECT_TRUE(c.tp.getTokens()[3] == "");
        }
        { // many scans but only match at the end
            Ctx c("a+b- a+b+c- a+b+c+", QB(1).add("a+b+c+"));
            EXPECT_TRUE(c.tp.getTokens().size() == 7);
            EXPECT_TRUE(c.tp.getTokens()[0] == "a");
            EXPECT_TRUE(c.tp.getTokens()[1] == "b");
            EXPECT_TRUE(c.tp.getTokens()[2] == "a");
            EXPECT_TRUE(c.tp.getTokens()[3] == "b");
            EXPECT_TRUE(c.tp.getTokens()[4] == "c");
            EXPECT_TRUE(c.tp.getTokens()[5] == "a+b+c+");
            EXPECT_TRUE(c.tp.getTokens()[6] == "");
        }
        { // two special tokens (one being a substring of the other)
            Ctx c("c+c+c-", QB(2).add("c+c+c+").add("+c+"));
            EXPECT_TRUE(c.tp.getTokens().size() == 4);
            EXPECT_TRUE(c.tp.getTokens()[0] == "c");
            EXPECT_TRUE(c.tp.getTokens()[1] == "+c+");
            EXPECT_TRUE(c.tp.getTokens()[2] == "c");
            EXPECT_TRUE(c.tp.getTokens()[3] == "");
        }
        { // cjk
            Ctx c("fish: \xE9\xB1\xBC!", QB(1).add("\xE9\xB1\xBC!"));
            EXPECT_TRUE(c.tp.getTokens().size() == 3);
            EXPECT_TRUE(c.tp.getTokens()[0] == "fish");
            EXPECT_TRUE(c.tp.getTokens()[1] == "\xE9\xB1\xBC!");
            EXPECT_TRUE(c.tp.getTokens()[2] == "");
        }
        { // special token with non-word first
            Ctx c("+++c ..net", QB(2).add("++c").add(".net"));
            EXPECT_TRUE(c.tp.getTokens().size() == 3);
            EXPECT_TRUE(c.tp.getTokens()[0] == "++c");
            EXPECT_TRUE(c.tp.getTokens()[1] == ".net");
            EXPECT_TRUE(c.tp.getTokens()[2] == "");
        }
    }
}

TEST(AuxTest, testWhiteSpacePreserved) {
    std::string input = "\x1f"
                        "best"
                        "\x1f"
                        "  "
                        "\x1f"
                        "of"
                        "\x1f"
                        "  "
                        "\n"
                        "\x1f"
                        "metallica"
                        "\x1f";

    juniper::PropertyMap myprops;
    myprops.set("juniper.dynsum.escape_markup", "off")
        .set("juniper.dynsum.highlight_off", "</hi>")
        .set("juniper.dynsum.continuation", "<sep />")
        .set("juniper.dynsum.highlight_on", "<hi>")
        .set("juniper.dynsum.preserve_white_space", "on");
    Fast_NormalizeWordFolder wf;
    juniper::Juniper         juniper(&myprops, &wf);
    juniper::Config          myConfig("myconfig", juniper);

    juniper::QueryParser q("best");
    juniper::QueryHandle qh(q, nullptr);
    auto                 res = juniper::Analyse(myConfig, qh, input.c_str(), input.size(), 0);
    EXPECT_TRUE(static_cast<bool>(res));

    juniper::Summary* sum = juniper::GetTeaser(*res, nullptr);
    std::string       expected = "<hi>best</hi>  of  \nmetallica";
    std::string       actual(sum->Text(), sum->Length());
    EXPECT_TRUE(actual == expected);
}

int main(int argc, char **argv) {
    ::testing::InitGoogleTest(&argc, argv);
    juniper::TestEnv te(argc, argv, TEST_PATH("testclient.rc").c_str());
    return RUN_ALL_TESTS();
}
