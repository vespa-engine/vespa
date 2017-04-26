// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storageframework/generic/thread/taskthread.h>
#include <vespa/vdstestlib/cppunit/macros.h>

namespace storage {
namespace framework {

struct TaskThreadTest : public CppUnit::TestFixture
{

    void testNormalUsage();

    CPPUNIT_TEST_SUITE(TaskThreadTest);
    CPPUNIT_TEST(testNormalUsage);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(TaskThreadTest);

namespace {
    struct Task {
        std::string _name;
        uint8_t _priority;
        
        Task(const std::string& name, uint8_t priority)
            : _name(name), _priority(priority) {}
        
        bool operator<(const Task& other) const {
            return (_priority > other._priority);
        }
        uint8_t getPriority() const { return _priority; }
    };

    struct MyThread : public TaskThread<Task> {
        MyThread(ThreadLock& lock) : TaskThread<Task>(lock) {}
        ThreadWaitInfo doNonCriticalTick(ThreadIndex) override {
            return ThreadWaitInfo::NO_MORE_CRITICAL_WORK_KNOWN;
        }
    };
}

void
TaskThreadTest::testNormalUsage()
{
    TickingThreadPool::UP pool(TickingThreadPool::createDefault("testApp"));

    MyThread t(*pool);
    t.addTask(Task("a", 6));
    t.addTask(Task("b", 3));
    t.addTask(Task("c", 8));
    t.addTask(Task("d", 4));
    CPPUNIT_ASSERT(t.empty()); // Still empty before critical tick has run
    dynamic_cast<TickingThread&>(t).doCriticalTick(0);
    CPPUNIT_ASSERT(!t.empty());
    CPPUNIT_ASSERT_EQUAL(3, (int) t.peek().getPriority());
    std::ostringstream ost;
    while (!t.empty()) {
        Task task(t.peek());
        ost << task._name << '(' << ((int) task.getPriority()) << ") ";
        t.pop();
    }
    CPPUNIT_ASSERT_EQUAL(std::string("b(3) d(4) a(6) c(8) "), ost.str());
}


} // framework
} // storage
