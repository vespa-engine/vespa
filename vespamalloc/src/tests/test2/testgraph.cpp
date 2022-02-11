// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespamalloc/util/index.h>
#include <vespamalloc/util/callgraph.h>
#include <vespamalloc/util/callstack.h>
#include <vespamalloc/util/traceutil.h>
#include <string>

using namespace vespamalloc;

//typedef StackEntry<StackFrameReturnEntry> StackElem;
typedef CallGraph<int, 0x1000, Index> CallGraphIntT;
typedef CallGraph<StackElem, 0x1000, Index> CallGraphStackEntryT;

namespace vespalibtest {

template <typename T>
class DumpGraph
{
public:
    DumpGraph(const char * s="") : _string(s) { }
    void handle(const T & node)
    {
        asciistream os;
        os << ' ' << node;
        _string += os.c_str();
        if (node.callers() == nullptr) {
            printf("%s\n", _string.c_str());
        }
    }
    const std::string & str() const { return _string; }
private:
    std::string _string;
};

}
void testint() {
    CallGraphIntT callGraph;
    vespalibtest::DumpGraph<CallGraphIntT::Node> dump("int: ");
    int s1[3] = { 1, 2, 3 };
    int s2[3] = { 1, 2, 4 };
    int s3[1] = { 1 };
    int s4[3] = { 1, 3, 4 };
    callGraph.addStack(s1, 3);
    callGraph.addStack(s2, 3);
    callGraph.addStack(s3, 1);
    callGraph.addStack(s4, 3);
    callGraph.traverseDepth(dump);
    printf("%s\n", dump.str().c_str());
}

void teststackentry() {
    CallGraphStackEntryT callGraph;
    vespalibtest::DumpGraph<CallGraphStackEntryT::Node> dump("callstack: ");
    StackElem s1[3] = { StackElem((void *)1), StackElem((void *)2), StackElem((void *)3) };
    StackElem s2[3] = { StackElem((void *)1), StackElem((void *)2), StackElem((void *)4) };
    StackElem s3[1] = { StackElem((void *)1) };
    StackElem s4[3] = { StackElem((void *)1), StackElem((void *)3), StackElem((void *)4) };
    callGraph.addStack(s1, 3);
    callGraph.addStack(s2, 3);
    callGraph.addStack(s3, 1);
    callGraph.addStack(s4, 3);
    callGraph.traverseDepth(dump);
    printf("%s\n", dump.str().c_str());
}

void testaggregator() {
    CallGraphStackEntryT callGraph;
    StackElem s1[3] = { StackElem((void *)1), StackElem((void *)2), StackElem((void *)3) };
    StackElem s2[3] = { StackElem((void *)1), StackElem((void *)2), StackElem((void *)4) };
    StackElem s3[1] = { StackElem((void *)1) };
    StackElem s4[3] = { StackElem((void *)1), StackElem((void *)3), StackElem((void *)4) };
    callGraph.addStack(s1, 3);
    callGraph.addStack(s2, 3);
    callGraph.addStack(s3, 1);
    callGraph.addStack(s4, 3);
    Aggregator agg;
    DumpGraph<CallGraphT::Node> dump(&agg, "{ ", " }");
    callGraph.traverseDepth(dump);
    asciistream ost;
    ost << agg;
    printf("%s\n", ost.c_str());
}
int main (int argc, char *argv[])
{
    (void) argc;
    (void) argv;
    testint();
    teststackentry();
    testaggregator();
    return 0;
}

