// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/stllike/lexical_cast.h>
#include <vespa/vespalib/test/test_path.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/locale/c.h>
#include <cmath>
#include <iomanip>
#include <float.h>

using namespace vespalib;

namespace {

template <typename T>
void
verifyBothWays(T value, const char * expected, const std::string& label)
{
    SCOPED_TRACE(label);
    asciistream os;
    os << value;
    EXPECT_EQ(os.view(), std::string(expected));
    EXPECT_EQ(os.size(), strlen(expected));
    {
        T v;
        os >> v;
        EXPECT_EQ(value, v);
        EXPECT_TRUE(os.empty());
    }

    {
        os << "   " << expected;
        T v;
        os >> v;
        EXPECT_EQ(value, v);
        EXPECT_TRUE(os.empty());
        EXPECT_EQ(0u, os.size());
    }
}

template <typename T>
void
verify(T first, T second, const char * firstResult, const char * secondResult, char delim, const std::string& label)
{
    SCOPED_TRACE(label);
    asciistream os;
    std::ostringstream ss;
    os << first;
    ss << first;
    EXPECT_EQ(os.view(), std::string(firstResult));
    EXPECT_EQ(os.size(), strlen(firstResult));
    EXPECT_EQ(ss.str().size(), strlen(firstResult));
    EXPECT_EQ(strcmp(ss.str().c_str(), firstResult), 0);
    os << delim << second;
    ss << delim << second;
    EXPECT_EQ(os.size(), strlen(secondResult));
    EXPECT_EQ(ss.str().size(), strlen(secondResult));
    EXPECT_EQ(strcmp(os.c_str(), secondResult), 0);
    EXPECT_EQ(strcmp(ss.str().c_str(), secondResult), 0);
}

}

TEST(AsciistreamTest, test_illegal_numbers)
{
    {
        asciistream is("777777777777");
        uint16_t s(0);
        EXPECT_THROW(is >> s, IllegalArgumentException);
        EXPECT_EQ(12u, is.size());
        uint32_t i(0);
        EXPECT_THROW(is >> i, IllegalArgumentException);
        EXPECT_EQ(12u, is.size());
        int16_t si(0);
        EXPECT_THROW(is >> si, IllegalArgumentException);
        EXPECT_EQ(12u, is.size());
        int32_t ii(0);
        EXPECT_THROW(is >> ii, IllegalArgumentException);
        EXPECT_EQ(12u, is.size());
        is << "777777777777";
        EXPECT_EQ(24u, is.size());
        uint64_t l(0);
        EXPECT_THROW(is >> l, IllegalArgumentException);
        EXPECT_EQ(24u, is.size());
        int64_t li(0);
        EXPECT_THROW(is >> li, IllegalArgumentException);
        EXPECT_EQ(24u, is.size());
    }
    {
        asciistream is("-77");
        uint16_t s(0);
        EXPECT_THROW(is >> s, IllegalArgumentException);
        EXPECT_EQ(3u, is.size());
        uint32_t i(0);
        EXPECT_THROW(is >> i, IllegalArgumentException);
        EXPECT_EQ(3u, is.size());
    }
    {
        asciistream is("7777777777777777777777777777777777777777");
        EXPECT_EQ(40u, is.size());
        float f(0);
        EXPECT_THROW(is >> f, IllegalArgumentException);
        EXPECT_EQ(40u, is.size());
       std::string_view tmp = is.view();
        is << "e" << tmp;
        EXPECT_EQ(81u, is.size());
        double d(0);
        EXPECT_THROW(is >> d, IllegalArgumentException);
        EXPECT_EQ(81u, is.size());
    }
    {
        asciistream is("a");
        char c(' ');
        EXPECT_EQ(1u, is.size());
        is >> c;
        EXPECT_EQ('a', c);
        EXPECT_TRUE(is.empty());
        EXPECT_THROW(is >> c, IllegalArgumentException);
        EXPECT_TRUE(is.empty());
        unsigned char u(' ');
        EXPECT_THROW(is >> u, IllegalArgumentException);
        EXPECT_TRUE(is.empty());
        bool b(false);
        EXPECT_THROW(is >> b, IllegalArgumentException);
        EXPECT_TRUE(is.empty());
        {
            uint32_t l(0);
            EXPECT_THROW(is >> l, IllegalArgumentException);
            EXPECT_TRUE(is.empty());
        }
        {
            int32_t l(0);
            EXPECT_THROW(is >> l, IllegalArgumentException);
            EXPECT_TRUE(is.empty());
        }
        {
            float l(0);
            EXPECT_THROW(is >> l, IllegalArgumentException);
            EXPECT_TRUE(is.empty());
        }
        {
            double l(0);
            EXPECT_THROW(is >> l, IllegalArgumentException);
            EXPECT_TRUE(is.empty());
        }

    }
}

TEST(AsciistreamTest, test_copy_construct)
{
    asciistream os;
    os << "test1";
    asciistream os2(os);
    EXPECT_EQ(os.view(), os2.view());
    os2 << " test2";
    EXPECT_FALSE(os.view() == os2.view());
    asciistream os3(os);
    os3 = os2;
    EXPECT_EQ(os2.view(), os3.view());
    os.swap(os2);
    EXPECT_EQ(os.view(), os3.view());
    EXPECT_FALSE(os3.view() == os2.view());
    os.swap(os2);
    EXPECT_TRUE(os3.view() == os2.view());
}

TEST(AsciistreamTest, test_move_is_well_defined)
{
    asciistream read_only("hello world");
    asciistream dest(std::move(read_only));
    EXPECT_EQ("hello world", dest.view());

    read_only = asciistream("a string long enough to not be short string optimized");
    dest = std::move(read_only);
    EXPECT_EQ("a string long enough to not be short string optimized", dest.view());

    asciistream written_src;
    written_src << "a foo walks into a bar";
    dest = std::move(written_src);
    EXPECT_EQ("a foo walks into a bar", dest.view());
}

TEST(AsciistreamTest, test_integer_manip)
{
    asciistream os;
    std::ostringstream ss;
    os << 10;
    ss << 10;
    EXPECT_EQ(os.size(), 2u);
    EXPECT_EQ(ss.str().size(), 2u);
    EXPECT_EQ(strcmp(os.c_str(), "10"), 0);
    EXPECT_EQ(strcmp(ss.str().c_str(), "10"), 0);
    os << ' ' << dec << 10;
    ss << ' ' << std::dec << 10;
    EXPECT_EQ(os.size(), 5u);
    EXPECT_EQ(ss.str().size(), 5u);
    EXPECT_EQ(strcmp(os.c_str(), "10 10"), 0);
    EXPECT_EQ(strcmp(ss.str().c_str(), "10 10"), 0);
    os << ' ' << hex << 10 << ' ' << 11;
    ss << ' ' << std::hex << 10 << ' ' << 11;
    EXPECT_EQ(os.size(), 9u);
    EXPECT_EQ(ss.str().size(), 9u);
    EXPECT_EQ(strcmp(os.c_str(), "10 10 a b"), 0);
    EXPECT_EQ(strcmp(ss.str().c_str(), "10 10 a b"), 0);
    os << ' ' << oct << 10;
    ss << ' ' << std::oct << 10;
    EXPECT_EQ(os.size(), 12u);
    EXPECT_EQ(ss.str().size(), 12u);
    EXPECT_EQ(strcmp(os.c_str(), "10 10 a b 12"), 0);
    EXPECT_EQ(strcmp(ss.str().c_str(), "10 10 a b 12"), 0);

    // std::bin not supported by std::streams.
    os << ' ' << bin << 10;
    EXPECT_EQ(os.size(), 19u);
    EXPECT_EQ(strcmp(os.c_str(), "10 10 a b 12 0b1010"), 0);

    void *fooptr = reinterpret_cast<void*>(0x1badbadc0ffeeull);
    // Also test that number base is restored OK after ptr print
    os << dec << ' ' << fooptr << ' ' << 1234;
    ss << std::dec << ' ' << fooptr << ' ' << 1234;
    EXPECT_EQ(std::string("10 10 a b 12 0b1010 0x1badbadc0ffee 1234"), os.view());
    EXPECT_EQ(std::string("10 10 a b 12 0x1badbadc0ffee 1234"), ss.str());

    int i = 0;
    const char *digits = "12345";
    std::string ffs(digits, 4);
    std::istringstream std_istr(ffs);
    std_istr >> i;
    EXPECT_EQ(1234, i);

    std::string_view firstfour(digits, 4);
    asciistream istr(firstfour);
    istr >> i;
    EXPECT_EQ(1234, i);
}


TEST(AsciistreamTest, test_fill)
{
    {
        asciistream os;
        std::ostringstream ss;
        os << 10 << ' ' << setfill('h') << 11;
        ss << 10 << ' ' << std::setfill('h') << 11;
        EXPECT_EQ(os.size(), 5u);
        EXPECT_EQ(ss.str().size(), 5u);
        EXPECT_EQ(strcmp(os.c_str(), "10 11"), 0);
        EXPECT_EQ(strcmp(ss.str().c_str(), "10 11"), 0);
        os << setw(4) << 10 << ' ' << 11;
        ss << std::setw(4) << 10 << ' ' << 11;
        EXPECT_EQ(os.size(), 12u);
        EXPECT_EQ(ss.str().size(), 12u);
        EXPECT_EQ(strcmp(os.c_str(), "10 11hh10 11"), 0);
        EXPECT_EQ(strcmp(ss.str().c_str(), "10 11hh10 11"), 0);
        os << setw(4) << 10 << ' ' << 11;
        ss << std::setw(4) << 10 << ' ' << 11;
        EXPECT_EQ(os.size(), 19u);
        EXPECT_EQ(ss.str().size(), 19u);
        EXPECT_EQ(strcmp(os.c_str(), "10 11hh10 11hh10 11"), 0);
        EXPECT_EQ(strcmp(ss.str().c_str(), "10 11hh10 11hh10 11"), 0);
    }
    {
        asciistream os;
        std::ostringstream ss;
        os << setfill('X') << setw(19) << 'a';
        ss << std::setfill('X') << std::setw(19) << 'a';
        EXPECT_EQ(os.size(), 19u);
        EXPECT_EQ(ss.str().size(), 19u);
        EXPECT_EQ(strcmp(os.c_str(), "XXXXXXXXXXXXXXXXXXa"), 0);
        EXPECT_EQ(strcmp(ss.str().c_str(), "XXXXXXXXXXXXXXXXXXa"), 0);
    }
    {
        asciistream os;
        std::ostringstream ss;
        os << setfill('X') << setw(19) << "a";
        ss << std::setfill('X') << std::setw(19) << "a";
        EXPECT_EQ(os.size(), 19u);
        EXPECT_EQ(ss.str().size(), 19u);
        EXPECT_EQ(strcmp(os.c_str(), "XXXXXXXXXXXXXXXXXXa"), 0);
        EXPECT_EQ(strcmp(ss.str().c_str(), "XXXXXXXXXXXXXXXXXXa"), 0);
    }
    {
        float f(8.9);
        asciistream os;
        std::ostringstream ss;
        os << setfill('X') << setw(19) << f;
        ss << std::setfill('X') << std::setw(19) << f;
        EXPECT_EQ(os.size(), 19u);
        EXPECT_EQ(ss.str().size(), 19u);
        EXPECT_EQ(strcmp(os.c_str(), "XXXXXXXXXXXXXXXX8.9"), 0);
        EXPECT_EQ(strcmp(ss.str().c_str(), "XXXXXXXXXXXXXXXX8.9"), 0);
    }
    {
        double f(8.9);
        asciistream os;
        std::ostringstream ss;
        os << setfill('X') << setw(19) << f;
        ss << std::setfill('X') << std::setw(19) << f;
        EXPECT_EQ(os.size(), 19u);
        EXPECT_EQ(ss.str().size(), 19u);
        EXPECT_EQ(strcmp(os.c_str(), "XXXXXXXXXXXXXXXX8.9"), 0);
        EXPECT_EQ(strcmp(ss.str().c_str(), "XXXXXXXXXXXXXXXX8.9"), 0);
    }

}

TEST(AsciistreamTest, test_string)
{

    std::string ss("a");
    std::string vs("a");
    {
        std::ostringstream oss;
        oss << ss << vs;
        EXPECT_EQ("aa", oss.str());
    }
    {
        asciistream oss;
        oss << ss << vs;
        EXPECT_EQ("aa", oss.view());
    }
    {
        std::istringstream iss("b c");
        iss >> ss >> vs;
        EXPECT_EQ("b", ss);
        EXPECT_EQ("c", vs);
    }
    {
        std::istringstream iss("b c");
        iss >> vs >> ss;
        EXPECT_EQ("b", vs);
        EXPECT_EQ("c", ss);
    }
    {
        asciistream iss("b c");
        iss >> ss >> vs;
        EXPECT_EQ("b", ss);
        EXPECT_EQ("c", vs);
    }
    {
        asciistream iss("b c");
        iss >> vs >> ss;
        EXPECT_EQ("b", vs);
        EXPECT_EQ("c", ss);
    }
}

TEST(AsciistreamTest, test_create_from_file)
{
    asciistream is(asciistream::createFromFile("non-existing.txt"));
    EXPECT_TRUE(is.eof());

    is = asciistream::createFromFile(TEST_PATH("test.txt"));
    EXPECT_FALSE(is.eof());
    EXPECT_EQ(12u, is.size());
    std::string s;
    is >> s;
    EXPECT_EQ("line1", s);
    is >> s;
    EXPECT_EQ("line2", s);
    EXPECT_FALSE(is.eof());
    is >> s;
    EXPECT_EQ("", s);
    EXPECT_TRUE(is.eof());

#ifdef __linux__
    is = asciistream::createFromDevice("/proc/stat");
    EXPECT_FALSE(is.eof());
#endif
}

TEST(AsciistreamTest, test_write_then_read)
{
    asciistream ios;
    ios << "3 words";
    int n(0);
    std::string v;
    ios >> n >> v;
    EXPECT_EQ(3, n);
    EXPECT_EQ("words", v);
    EXPECT_TRUE(ios.eof());
}

TEST(AsciistreamTest, test_get_line)
{
    asciistream is = asciistream("line 1\nline 2\nline 3");
    std::string s;
    getline(is, s);
    EXPECT_EQ("line 1", s);
    getline(is, s);
    EXPECT_EQ("line 2", s);
    getline(is, s);
    EXPECT_EQ("line 3", s);
}

#define VERIFY_DOUBLE_SERIALIZATION(value, expected, format, precision) { \
    asciistream mystream; \
    mystream << format; \
    if (precision > 0) mystream << asciistream::Precision(precision); \
    mystream << value; \
    EXPECT_EQ(expected, mystream.view()); \
}

TEST(AsciistreamTest, test_double)
{
    VERIFY_DOUBLE_SERIALIZATION(0.0, "0.000000", fixed, -1);
    VERIFY_DOUBLE_SERIALIZATION(0.0, "0.000000e+00", scientific, -1);
    VERIFY_DOUBLE_SERIALIZATION(0.0, "0", automatic, -1);

    VERIFY_DOUBLE_SERIALIZATION(0.0, "0.0", fixed, 1);
    VERIFY_DOUBLE_SERIALIZATION(0.0, "0.0e+00", scientific, 1);
    VERIFY_DOUBLE_SERIALIZATION(0.0, "0", automatic, 1);

    VERIFY_DOUBLE_SERIALIZATION(0.0, "0.0000000000000000", fixed, 16);
    VERIFY_DOUBLE_SERIALIZATION(0.0, "0.0000000000000000e+00", scientific, 16);
    VERIFY_DOUBLE_SERIALIZATION(0.0, "0", automatic, 16);

    double maxVal = std::numeric_limits<double>::max();
    VERIFY_DOUBLE_SERIALIZATION(maxVal, "179769313486231570814527423731704356798070567525844996598917476803157260780028538760589558632766878171540458953514382464234321326889464182768467546703537516986049910576551282076245490090389328944075868508455133942304583236903222948165808559332123348274797826204144723168738177180919299881250404026184124858368.000000", fixed, -1);
    VERIFY_DOUBLE_SERIALIZATION(maxVal, "1.797693e+308", scientific, -1);
    VERIFY_DOUBLE_SERIALIZATION(maxVal, "1.79769e+308", automatic, -1);

    VERIFY_DOUBLE_SERIALIZATION(maxVal, "179769313486231570814527423731704356798070567525844996598917476803157260780028538760589558632766878171540458953514382464234321326889464182768467546703537516986049910576551282076245490090389328944075868508455133942304583236903222948165808559332123348274797826204144723168738177180919299881250404026184124858368.0", fixed, 1);
    VERIFY_DOUBLE_SERIALIZATION(maxVal, "1.8e+308", scientific, 1);
    VERIFY_DOUBLE_SERIALIZATION(maxVal, "2e+308", automatic, 1);

    VERIFY_DOUBLE_SERIALIZATION(maxVal, "179769313486231570814527423731704356798070567525844996598917476803157260780028538760589558632766878171540458953514382464234321326889464182768467546703537516986049910576551282076245490090389328944075868508455133942304583236903222948165808559332123348274797826204144723168738177180919299881250404026184124858368.0000000000000000", fixed, 16);
    VERIFY_DOUBLE_SERIALIZATION(maxVal, "1.7976931348623157e+308", scientific, 16)
    VERIFY_DOUBLE_SERIALIZATION(maxVal, "1.797693134862316e+308", automatic, 16);

    double minVal = std::numeric_limits<double>::min();
    VERIFY_DOUBLE_SERIALIZATION(minVal, "0.000000", fixed, -1);
    VERIFY_DOUBLE_SERIALIZATION(minVal, "2.225074e-308", scientific, -1);
    VERIFY_DOUBLE_SERIALIZATION(minVal, "2.22507e-308", automatic, -1);

    VERIFY_DOUBLE_SERIALIZATION(minVal, "0.0", fixed, 1);
    VERIFY_DOUBLE_SERIALIZATION(minVal, "2.2e-308", scientific, 1);
    VERIFY_DOUBLE_SERIALIZATION(minVal, "2e-308", automatic, 1);

    VERIFY_DOUBLE_SERIALIZATION(minVal, "0.0000000000000000", fixed, 16);
    VERIFY_DOUBLE_SERIALIZATION(minVal, "2.2250738585072014e-308", scientific, 16);
    VERIFY_DOUBLE_SERIALIZATION(minVal, "2.225073858507201e-308", automatic, 16);

    double maxInteger = uint64_t(1) << 53;
    VERIFY_DOUBLE_SERIALIZATION(maxInteger, "9007199254740992.000000", fixed, -1);
    VERIFY_DOUBLE_SERIALIZATION(maxInteger, "9.007199e+15", scientific, -1);
    VERIFY_DOUBLE_SERIALIZATION(maxInteger, "9.0072e+15", automatic, -1);

    VERIFY_DOUBLE_SERIALIZATION(maxInteger, "9007199254740992.0", fixed, 1);
    VERIFY_DOUBLE_SERIALIZATION(maxInteger, "9.0e+15", scientific, 1);
    VERIFY_DOUBLE_SERIALIZATION(maxInteger, "9e+15", automatic, 1);

    VERIFY_DOUBLE_SERIALIZATION(maxInteger, "9007199254740992.0000000000000000", fixed, 16);
    VERIFY_DOUBLE_SERIALIZATION(maxInteger, "9.0071992547409920e+15", scientific, 16);
    VERIFY_DOUBLE_SERIALIZATION(maxInteger, "9007199254740992", automatic, 16);

    VERIFY_DOUBLE_SERIALIZATION(0.0, "0.0", automatic << forcedot, -1);
    VERIFY_DOUBLE_SERIALIZATION(0.0, "0.0", automatic << forcedot, 1);
    VERIFY_DOUBLE_SERIALIZATION(0.0, "0.0", automatic << forcedot, 16);
    VERIFY_DOUBLE_SERIALIZATION(maxInteger, "9007199254740992.0", automatic << forcedot, 16);

    asciistream as;
    as.clear();
    as << (3 * std::numeric_limits<double>::min());
    double dv = 0;
    as >> dv;
    EXPECT_TRUE(dv > 0);

    as.clear();
    as << (3 * std::numeric_limits<double>::denorm_min());
    dv = 0;
    as >> dv;
    EXPECT_TRUE(dv > 0);

    as.clear();
    as << "1.0e-325";
    dv = 42.0;
    as >> dv;
    EXPECT_EQ(dv, 0.0);

    as.clear();
    as << "1.0e666";
    dv = 42.0;
    EXPECT_THROW(as >> dv, IllegalArgumentException);
    EXPECT_EQ(dv, 42.0);
}

TEST(AsciistreamTest, test_float)
{
    float f = 0;
    asciistream as("-5.490412E-39");
    as >> f;
    EXPECT_EQ(f, -5.490412E-39f);

    as.clear();
    as << "0.0001E-50";
    f = 42.0;
    as >> f;
    EXPECT_EQ(f, 0.0);

    as.clear();
    as << "123.4E50";
    f = 42.0;
    EXPECT_THROW(as >> f, IllegalArgumentException);
    EXPECT_EQ(f, 42.0);

    errno = 0;
    char *ep;
    f = locale::c::strtof_au("-5.490412E-39", &ep);
    EXPECT_EQ(f, -5.490412E-39f);
    EXPECT_EQ(errno, 0);
    EXPECT_EQ(*ep, 0);

    f = locale::c::strtof_au("0.0001E-50", &ep);
    EXPECT_EQ(f, 0.0);
    EXPECT_EQ(errno, 0);
    EXPECT_EQ(*ep, 0);

    f = locale::c::strtof_au("123.4E50", &ep);
    EXPECT_EQ(f, HUGE_VALF);
    EXPECT_EQ(errno, ERANGE);
    EXPECT_EQ(*ep, 0);
}

TEST(AsciistreamTest, test_state_saver)
{
    asciistream as;
    as << vespalib::hex << vespalib::setfill('0');
    {
        asciistream::StateSaver stateSaver(as);
        as << vespalib::dec << vespalib::setfill('1');
        EXPECT_EQ(vespalib::dec, as.getBase());
        EXPECT_EQ('1', as.getFill());
    }
    ASSERT_EQ(vespalib::hex, as.getBase());
    ASSERT_EQ('0', as.getFill());
}

TEST(AsciistreamTest, test_ascii_stream)
{
    verify("per", "paal", "per", "per paal", ' ', "string");
    verify<float>(7.89, -1.3, "7.89", "7.89 -1.3", ' ', "float");
    verify<double>(7.89, -1.3, "7.89", "7.89 -1.3", ' ', "double");
    verify<bool>(true, false, "1", "1 0", ' ', "bool");
    verify<char>(65, 66, "A", "A B", ' ', "char");
    verify<unsigned char>(65, 66, "A", "A B", ' ', "unsigned char");
    verify<signed char>(65, 66, "A", "A B", ' ', "signed char");
//    verify<int8_t>(65, -1, "65", "65 -1", ' ', "int8_t");
    verify<int16_t>(0, -1, "0", "0 -1", ' ', "int16_t");
    verify<int16_t>(789, -1, "789", "789 -1", ' ', "int16_t again");
    verify<int32_t>(789, -1, "789", "789 -1", ' ', "int32_t");
    verify<int64_t>(789789789789789l, -1, "789789789789789", "789789789789789 -1", ' ', "int64_t");
//    verify<uint8_t>(65, -1, "65", "65 255", ' ', "uint8_t");
    verify<uint16_t>(789, -1, "789", "789 65535", ' ', "uint16_t");
    verify<uint32_t>(789, -1, "789", "789 4294967295", ' ', "uint32_t");
    verify<uint64_t>(789789789789789l, -1, "789789789789789", "789789789789789 18446744073709551615", ' ', "uint64_t");

    verifyBothWays<std::string>("7.89", "7.89", "std::string");
    verifyBothWays<float>(7.89, "7.89", "float");
    verifyBothWays<double>(7.89, "7.89", "double");
    verifyBothWays<bool>(true, "1", "bool");
    verifyBothWays<bool>(false, "0", "bool again");
    verifyBothWays<char>(65, "A", "char");
    verifyBothWays<unsigned char>(65, "A", "unsigned char");
    // verifyBothWays<int8_t>(7, "7", "int8_t");
    // verifyBothWays<uint8_t>(7, "7", "uint8_t");
    verifyBothWays<int16_t>(7, "7", "int16_t");
    verifyBothWays<uint16_t>(7, "7", "uint16_t");
    verifyBothWays<int32_t>(7, "7", "int32_t");
    verifyBothWays<uint32_t>(7, "7", "uint32_t");
    verifyBothWays<int64_t>(7, "7", "int64_t");
    verifyBothWays<uint64_t>(7, "7", "uint64_t");
}


template<typename T>
void lexCastValid(T expect, std::string_view input) {
    SCOPED_TRACE(input);
    // fprintf(stderr, "lexical cast should pass: >>%.*s<<\n", (int)input.size(), input.data());
    T got = vespalib::lexical_cast<T>(input);
    EXPECT_EQ(expect, got);
}

template<typename T>
void lexCastInvalid(std::string_view input) {
    SCOPED_TRACE(input);
    // fprintf(stderr, "lexical cast should fail: >>%.*s<<\n", (int)input.size(), input.data());
    EXPECT_THROW(vespalib::lexical_cast<T>(input), vespalib::Exception);
}


std::string_view fsv(float v) {
    static char buf[100];
    auto res = std::to_chars(buf, buf+100, v);
    return std::string_view(buf, res.ptr);
}
std::string_view dsv(double v) {
    static char buf[100];
    auto res = std::to_chars(buf, buf+100, v);
    return std::string_view(buf, res.ptr);
}


TEST(LexicalCastTest, test_valid_casts)
{
    lexCastValid<int8_t>(0, "0");
    lexCastValid<int8_t>(7, "7");
    lexCastValid<int8_t>(11, "011");
    lexCastValid<int8_t>(-7, "-7");
    lexCastValid<int8_t>(127, "127");
    lexCastValid<int8_t>(-128, "-128");

    lexCastValid<uint8_t>(0, "0");
    lexCastValid<uint8_t>(127, "127");
    lexCastValid<uint8_t>(128, "128");
    lexCastValid<uint8_t>(255, "255");

    lexCastValid<int16_t>(0, "0");
    lexCastValid<int16_t>(42, "42");
    lexCastValid<int16_t>(32767, "32767");
    lexCastValid<int16_t>(-1, "-1");
    lexCastValid<int16_t>(-32768, "-32768");

    lexCastValid<uint16_t>(0, "0");
    lexCastValid<uint16_t>(42, "42");
    lexCastValid<uint16_t>(32768, "32768");
    lexCastValid<uint16_t>(65535, "65535");

    lexCastValid<int32_t>(0, "0");
    lexCastValid<int32_t>(-1, "-1");
    lexCastValid<int32_t>(2147483647, "2147483647");
    lexCastValid<int32_t>(-2147483648, "-2147483648");

    lexCastValid<uint32_t>(0, "0");
    lexCastValid<uint32_t>(12345, "12345");
    lexCastValid<uint32_t>(4294967295u, "4294967295");

    lexCastValid<int64_t>(0, "0");
    lexCastValid<int64_t>(-1, "-1");
    lexCastValid<int64_t>(9223372036854775807L, "9223372036854775807");
    lexCastValid<int64_t>(-1L-9223372036854775807L, "-9223372036854775808");

    lexCastValid<uint64_t>(0, "0");
    lexCastValid<uint64_t>(9223372036854775808UL, "9223372036854775808");
    lexCastValid<uint64_t>(18446744073709551615UL, "18446744073709551615");

    float dnmFlt = std::numeric_limits<float>::denorm_min();
    float minFlt = std::numeric_limits<float>::min();
    float maxFlt = std::numeric_limits<float>::max();
    float infFlt = std::numeric_limits<float>::infinity();

    lexCastValid<float>(0.0f, "0");
    lexCastValid<float>(0.0f, "0.0");
    lexCastValid<float>(1.0f, "1");
    lexCastValid<float>(1.0f, "1.000");
    lexCastValid<float>(-1.0f, "-1");
    lexCastValid<float>(minFlt, fsv(FLT_MIN));
    lexCastValid<float>(dnmFlt, fsv(FLT_TRUE_MIN));
    lexCastValid<float>(maxFlt, fsv(FLT_MAX));
    lexCastValid<float>(infFlt, "inf");
    lexCastValid<float>(-minFlt, fsv(-FLT_MIN));
    lexCastValid<float>(-dnmFlt, fsv(-FLT_TRUE_MIN));
    lexCastValid<float>(-maxFlt, fsv(-FLT_MAX));
    lexCastValid<float>(-infFlt, "-inf");

    double dnmDbl = std::numeric_limits<double>::denorm_min();
    double maxDbl = std::numeric_limits<double>::max();
    double minDbl = std::numeric_limits<double>::min();
    double infDbl = std::numeric_limits<double>::infinity();

    lexCastValid<double>(0.0, "0");
    lexCastValid<double>(0.0, "0.0");
    lexCastValid<double>(1.0, "1");
    lexCastValid<double>(1.0, "1.000");
    lexCastValid<double>(-1.0, "-1");

    lexCastValid<double>(minDbl, dsv(DBL_MIN));
    lexCastValid<double>(dnmDbl, dsv(DBL_TRUE_MIN));
    lexCastValid<double>(maxDbl, dsv(DBL_MAX));
    lexCastValid<double>(infDbl, "inf");
    lexCastValid<double>(-minDbl, dsv(-DBL_MIN));
    lexCastValid<double>(-dnmDbl, dsv(-DBL_TRUE_MIN));
    lexCastValid<double>(-maxDbl, dsv(-DBL_MAX));
    lexCastValid<double>(-infDbl, "-inf");
}

TEST(LexicalCastTest, test_invalid_casts)
{
    lexCastInvalid<int8_t>("-129");
    lexCastInvalid<int8_t>("128");
    lexCastInvalid<uint8_t>("-1");
    lexCastInvalid<uint8_t>("256");
    lexCastInvalid<int16_t>("-32769");
    lexCastInvalid<int16_t>("32768");
    lexCastInvalid<uint16_t>("-1");
    lexCastInvalid<uint16_t>("65536");
    lexCastInvalid<int32_t>("-2147483649");
    lexCastInvalid<int32_t>("2147483648");
    lexCastInvalid<uint32_t>("-1");
    lexCastInvalid<uint32_t>("4294967296");
    lexCastInvalid<int64_t>("-9223372036854775809");
    lexCastInvalid<int64_t>("9223372036854775808");
    lexCastInvalid<uint64_t>("-1");
    lexCastInvalid<uint64_t>("18446744073709551616");

    lexCastInvalid<int8_t>("0.0");
    lexCastInvalid<int8_t>("0x0");
    lexCastInvalid<int8_t>("0a");
    lexCastInvalid<uint8_t>("1.0");
    lexCastInvalid<uint8_t>("1f");
}

GTEST_MAIN_RUN_ALL_TESTS()
