// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/trace/trace.h>
#include <vespa/vespalib/trace/tracevisitor.h>

#include <vespa/log/log.h>
LOG_SETUP("trace_test");

using namespace vespalib;

TEST(TraceTest, testEncodeDecode)
{
    EXPECT_EQ("()", TraceNode::decode("").encode());
    EXPECT_EQ("()", TraceNode::decode("[xyz").encode());
    EXPECT_EQ("([xyz][])", TraceNode::decode("[xyz][]").encode());
    EXPECT_EQ("[xyz]", TraceNode::decode("[xyz]").encode());
    EXPECT_EQ("()", TraceNode::decode("{()").encode());
    EXPECT_EQ("({()}{})", TraceNode::decode("{()}{}").encode());
    EXPECT_EQ("{()}", TraceNode::decode("{()}").encode());
    EXPECT_EQ("()", TraceNode::decode("({}").encode());
    EXPECT_EQ("(({})())", TraceNode::decode("({})()").encode());
    EXPECT_EQ("([])", TraceNode::decode("([])").encode());

    EXPECT_TRUE(TraceNode::decode("").isEmpty());
    EXPECT_TRUE(!TraceNode::decode("([note])").isEmpty());

    std::string str =
        "([[17/Jun/2009:09:02:30 +0200\\] Message (type 1) received at 'dst' for session 'session'.]"
        "[[17/Jun/2009:09:02:30 +0200\\] [APP_TRANSIENT_ERROR @ localhost\\]: err1]"
        "[[17/Jun/2009:09:02:30 +0200\\] Sending reply (version 4.2) from 'dst'.])";
    fprintf(stderr, "%s\n", TraceNode::decode(str).toString().c_str());
    EXPECT_EQ(str, TraceNode::decode(str).encode());

    str = "([Note 0][Note 1]{[Note 2]}{([Note 3])({[Note 4]})})";
    TraceNode t = TraceNode::decode(str);
    EXPECT_EQ(str, t.encode());

    EXPECT_TRUE(t.isRoot());
    EXPECT_TRUE(t.isStrict());
    EXPECT_TRUE(!t.isLeaf());
    EXPECT_EQ(4u, t.getNumChildren());

    {
        TraceNode c = t.getChild(0);
        EXPECT_TRUE(c.isLeaf());
        EXPECT_EQ("Note 0", c.getNote());
    }
    {
        TraceNode c = t.getChild(1);
        EXPECT_TRUE(c.isLeaf());
        EXPECT_EQ("Note 1", c.getNote());
    }
    {
        TraceNode c = t.getChild(2);
        EXPECT_TRUE(!c.isLeaf());
        EXPECT_TRUE(!c.isStrict());
        EXPECT_EQ(1u, c.getNumChildren());
        {
            TraceNode d = c.getChild(0);
            EXPECT_TRUE(d.isLeaf());
            EXPECT_EQ("Note 2", d.getNote());
        }
    }
    {
        TraceNode c = t.getChild(3);
        EXPECT_TRUE(!c.isStrict());
        EXPECT_EQ(2u, c.getNumChildren());
        {
            TraceNode d = c.getChild(0);
            EXPECT_TRUE(d.isStrict());
            EXPECT_TRUE(!d.isLeaf());
            EXPECT_EQ(1u, d.getNumChildren());
            {
                TraceNode e = d.getChild(0);
                EXPECT_TRUE(e.isLeaf());
                EXPECT_EQ("Note 3", e.getNote());
            }
        }
        {
            TraceNode d = c.getChild(1);
            EXPECT_TRUE(d.isStrict());
            EXPECT_EQ(1u, d.getNumChildren());
            {
                TraceNode e = d.getChild(0);
                EXPECT_TRUE(!e.isStrict());
                EXPECT_EQ(1u, e.getNumChildren());
                {
                    TraceNode f = e.getChild(0);
                    EXPECT_TRUE(f.isLeaf());
                    EXPECT_EQ("Note 4", f.getNote());
                }
            }
        }
    }
}

TEST(TraceTest, testReservedChars)
{
    TraceNode t;
    t.addChild("abc(){}[]\\xyz");
    EXPECT_EQ("abc(){}[]\\xyz", t.getChild(0).getNote());
    EXPECT_EQ("([abc(){}[\\]\\\\xyz])", t.encode());
    {
        // test swap/clear/empty here
        TraceNode t2;
        EXPECT_TRUE(t2.isEmpty());
        t2.swap(t);
        EXPECT_TRUE(!t2.isEmpty());
        EXPECT_EQ("abc(){}[]\\xyz", t2.getChild(0).getNote());
        EXPECT_EQ("([abc(){}[\\]\\\\xyz])", t2.encode());
        t2.clear();
        EXPECT_TRUE(t2.isEmpty());
    }
}

TEST(TraceTest, testAdd)
{
    TraceNode t1 = TraceNode::decode("([x])");
    TraceNode t2 = TraceNode::decode("([y])");
    TraceNode t3 = TraceNode::decode("([z])");

    t1.addChild(t2);
    EXPECT_EQ("([x]([y]))", t1.encode());
    EXPECT_TRUE(t1.getChild(1).isStrict());
    t1.addChild("txt");
    EXPECT_TRUE(t1.getChild(2).isLeaf());
    EXPECT_EQ("([x]([y])[txt])", t1.encode());
    t3.addChild(t1);
    EXPECT_EQ("([z]([x]([y])[txt]))", t3.encode());

    // crazy but possible (everything is by value)
    t2.addChild(t2).addChild(t2);
    EXPECT_EQ("([y]([y])([y]([y])))", t2.encode());
}

TEST(TraceTest, testStrict)
{
    EXPECT_EQ("{}", TraceNode::decode("()").setStrict(false).encode());
    EXPECT_EQ("{[x]}", TraceNode::decode("([x])").setStrict(false).encode());
    EXPECT_EQ("{[x][y]}", TraceNode::decode("([x][y])").setStrict(false).encode());
}

TEST(TraceTest, testTraceLevel)
{
    Trace t;
    t.setLevel(4);
    EXPECT_EQ(4u, t.getLevel());
    t.trace(9, "no");
    EXPECT_EQ(0u, t.getNumChildren());
    t.trace(8, "no");
    EXPECT_EQ(0u, t.getNumChildren());
    t.trace(7, "no");
    EXPECT_EQ(0u, t.getNumChildren());
    t.trace(6, "no");
    EXPECT_EQ(0u, t.getNumChildren());
    t.trace(5, "no");
    EXPECT_EQ(0u, t.getNumChildren());
    t.trace(4, "yes");
    EXPECT_EQ(1u, t.getNumChildren());
    t.trace(3, "yes");
    EXPECT_EQ(2u, t.getNumChildren());
    t.trace(2, "yes");
    EXPECT_EQ(3u, t.getNumChildren());
    t.trace(1, "yes");
    EXPECT_EQ(4u, t.getNumChildren());
    t.trace(0, "yes");
    EXPECT_EQ(5u, t.getNumChildren());
}

TEST(TraceTest, testCompact)
{
    EXPECT_EQ("()", TraceNode::decode("()").compact().encode());
    EXPECT_EQ("()", TraceNode::decode("(())").compact().encode());
    EXPECT_EQ("()", TraceNode::decode("(()())").compact().encode());
    EXPECT_EQ("()", TraceNode::decode("({})").compact().encode());
    EXPECT_EQ("()", TraceNode::decode("({}{})").compact().encode());
    EXPECT_EQ("()", TraceNode::decode("({{}{}})").compact().encode());

    EXPECT_EQ("([x])", TraceNode::decode("([x])").compact().encode());
    EXPECT_EQ("([x])", TraceNode::decode("(([x]))").compact().encode());
    EXPECT_EQ("([x][y])", TraceNode::decode("(([x])([y]))").compact().encode());
    EXPECT_EQ("([x])", TraceNode::decode("({[x]})").compact().encode());
    EXPECT_EQ("([x][y])", TraceNode::decode("({[x]}{[y]})").compact().encode());
    EXPECT_EQ("({[x][y]})", TraceNode::decode("({{[x]}{[y]}})").compact().encode());

    EXPECT_EQ("([a][b][c][d])", TraceNode::decode("(([a][b])([c][d]))").compact().encode());
    EXPECT_EQ("({[a][b]}{[c][d]})", TraceNode::decode("({[a][b]}{[c][d]})").compact().encode());
    EXPECT_EQ("({[a][b][c][d]})", TraceNode::decode("({{[a][b]}{[c][d]}})").compact().encode());
    EXPECT_EQ("({([a][b])([c][d])})", TraceNode::decode("({([a][b])([c][d])})").compact().encode());

    EXPECT_EQ("({{}{(({()}({}){()(){}}){})}})", TraceNode::decode("({{}{(({()}({}){()(){}}){})}})").encode());
    EXPECT_EQ("()", TraceNode::decode("({{}{(({()}({}){()(){}}){})}})").compact().encode());
    EXPECT_EQ("([x])", TraceNode::decode("({{}{([x]({()}({}){()(){}}){})}})").compact().encode());
    EXPECT_EQ("([x])", TraceNode::decode("({{}{(({()}({[x]}){()(){}}){})}})").compact().encode());
    EXPECT_EQ("([x])", TraceNode::decode("({{}{(({()}({}){()(){}})[x]{})}})").compact().encode());

    EXPECT_EQ("({[a][b][c][d][e][f]})", TraceNode::decode("({({[a][b]})({[c][d]})({[e][f]})})").compact().encode());
}

TEST(TraceTest, testSort)
{
    EXPECT_EQ("([b][a][c])", TraceNode::decode("([b][a][c])").sort().encode());
    EXPECT_EQ("({[a][b][c]})", TraceNode::decode("({[b][a][c]})").sort().encode());
    EXPECT_EQ("(([c][a])([b]))", TraceNode::decode("(([c][a])([b]))").sort().encode());
    EXPECT_EQ("({[b]([c][a])})", TraceNode::decode("({([c][a])[b]})").sort().encode());
    EXPECT_EQ("({[a][c]}[b])", TraceNode::decode("({[c][a]}[b])").sort().encode());
    EXPECT_EQ("({([b]){[a][c]}})", TraceNode::decode("({{[c][a]}([b])})").sort().encode());
}

TEST(TraceTest, testNormalize)
{
    TraceNode t1 = TraceNode::decode("({([a][b]{[x][y]([p][q])})([c][d])([e][f])})");
    TraceNode t2 = TraceNode::decode("({([a][b]{[y][x]([p][q])})([c][d])([e][f])})");
    TraceNode t3 = TraceNode::decode("({([a][b]{[y]([p][q])[x]})([c][d])([e][f])})");
    TraceNode t4 = TraceNode::decode("({([e][f])([a][b]{[y]([p][q])[x]})([c][d])})");
    TraceNode t5 = TraceNode::decode("({([e][f])([c][d])([a][b]{([p][q])[y][x]})})");

    TraceNode tx = TraceNode::decode("({([b][a]{[x][y]([p][q])})([c][d])([e][f])})");
    TraceNode ty = TraceNode::decode("({([a][b]{[x][y]([p][q])})([d][c])([e][f])})");
    TraceNode tz = TraceNode::decode("({([a][b]{[x][y]([q][p])})([c][d])([e][f])})");

    EXPECT_EQ("({([a][b]{[x][y]([p][q])})([c][d])([e][f])})", t1.compact().encode());

    EXPECT_TRUE(t1.compact().encode() != t2.compact().encode());
    EXPECT_TRUE(t1.compact().encode() != t3.compact().encode());
    EXPECT_TRUE(t1.compact().encode() != t4.compact().encode());
    EXPECT_TRUE(t1.compact().encode() != t5.compact().encode());
    EXPECT_TRUE(t1.compact().encode() != tx.compact().encode());
    EXPECT_TRUE(t1.compact().encode() != ty.compact().encode());
    EXPECT_TRUE(t1.compact().encode() != tz.compact().encode());

    fprintf(stderr, "1: %s\n", + t1.normalize().encode().c_str());
    fprintf(stderr, "2: %s\n", + t2.normalize().encode().c_str());
    fprintf(stderr, "3: %s\n", + t3.normalize().encode().c_str());
    fprintf(stderr, "4: %s\n", + t4.normalize().encode().c_str());
    fprintf(stderr, "5: %s\n", + t5.normalize().encode().c_str());
    fprintf(stderr, "x: %s\n", + tx.normalize().encode().c_str());
    fprintf(stderr, "y: %s\n", + ty.normalize().encode().c_str());
    fprintf(stderr, "z: %s\n", + tz.normalize().encode().c_str());
    EXPECT_TRUE(t1.normalize().encode() == t2.normalize().encode());
    EXPECT_TRUE(t1.normalize().encode() == t3.normalize().encode());
    EXPECT_TRUE(t1.normalize().encode() == t4.normalize().encode());
    EXPECT_TRUE(t1.normalize().encode() == t5.normalize().encode());
    EXPECT_TRUE(t1.normalize().encode() != tx.normalize().encode());
    EXPECT_TRUE(t1.normalize().encode() != ty.normalize().encode());
    EXPECT_TRUE(t1.normalize().encode() != tz.normalize().encode());

    EXPECT_EQ("({([c][d])([e][f])([a][b]{[x][y]([p][q])})})", t1.normalize().encode());
}

TEST(TraceTest, testTraceDump)
{
    {
        Trace big;
        TraceNode b1;
        TraceNode b2;
        for (int i = 0; i < 100; ++i) {
            b2.addChild("test");
        }
        for (int i = 0; i < 10; ++i) {
            b1.addChild(b2);
        }
        for (int i = 0; i < 10; ++i) {
            big.addChild(TraceNode(b1));
        }
        std::string normal = big.toString();
        std::string full = big.toString(100000);
        EXPECT_GT(normal.size(), 30000u);
        EXPECT_LT(normal.size(), 32000u);
        EXPECT_GT(full.size(), 50000u);
        EXPECT_EQ(0, strncmp(normal.c_str(), full.c_str(), 30000));
    }
    {
        TraceNode s1;
        TraceNode s2;
        s2.addChild("test");
        s2.addChild("test");
        s1.addChild(s2);
        s1.addChild(s2);
        EXPECT_EQ(std::string("...\n"), s1.toString(0));
        EXPECT_EQ(std::string("<trace>\n...\n"), s1.toString(1));
        EXPECT_EQ(std::string("<trace>\n"      // 8    8
                                      "    <trace>\n"  // 12  20
                                      "        test\n" // 13  33
                                      "...\n"), s1.toString(33));
        EXPECT_EQ(std::string("<trace>\n"      // 8   8
                                      "    test\n"     // 9  17
                                      "    test\n"     // 9  26
                                      "...\n"), s2.toString(26));
        EXPECT_EQ(std::string("<trace>\n"      // 8   8
                                      "    test\n"     // 9  17
                                      "    test\n"     // 9  26
                                      "</trace>\n"), s2.toString(27));
        EXPECT_EQ(s2.toString(27), s2.toString());
    }
}

struct EncoderVisitor : public TraceVisitor
{
    std::string str;
    void entering(const TraceNode & traceNode) override {
        (void) traceNode;
        str += "(";
    }
    void visit(const TraceNode & traceNode) override {
        if (traceNode.hasNote()) {
            str += "[";
            str += traceNode.getNote();
            str += "]";
        }
    }
    void leaving(const TraceNode & traceNode) override {
        (void) traceNode;
        str += ")";
    }
};

TEST(TraceTest, testVisiting)
{
    TraceNode b1;
    TraceNode b2;
    for (int i = 0; i < 100; ++i) {
        std::stringstream ss;
        ss << i;
        TraceNode b3;
        b3.addChild(ss.str());
        b2.addChild(b3);
    }
    for (int i = 0; i < 10; ++i) {
        b1.addChild(b2);
    }
    EncoderVisitor encoder;
    b1.accept(encoder);
    EXPECT_EQ(encoder.str, b1.encode());
}

constexpr system_time zero;
constexpr system_time as_ms(long ms) { return system_time(std::chrono::milliseconds(ms)); }

TEST(TraceTest, testTimestamp)
{
    TraceNode root;
    root.addChild("foo", as_ms(1234));
    root.addChild("bar");
    EXPECT_EQ(root.getTimestamp(), zero);
    EXPECT_EQ(root.getChild(0).getTimestamp(), as_ms(1234));
    EXPECT_EQ(root.getChild(1).getTimestamp(), zero);
}

TEST(TraceTest, testConstruct)
{
    TraceNode leaf1("foo", as_ms(123));
    EXPECT_TRUE(leaf1.hasNote());
    EXPECT_EQ("foo", leaf1.getNote());
    EXPECT_EQ(as_ms(123), leaf1.getTimestamp());

    TraceNode leaf2(as_ms(124));
    EXPECT_FALSE(leaf2.hasNote());
    EXPECT_EQ("", leaf2.getNote());
    EXPECT_EQ(as_ms(124), leaf2.getTimestamp());
}

GTEST_MAIN_RUN_ALL_TESTS()
