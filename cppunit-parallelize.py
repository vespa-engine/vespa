import sys
import argparse
import copy
import os
import subprocess
import time
import collections

def parse_arguments():
    argparser = argparse.ArgumentParser(description="Run Vespa cppunit tests in parallell")
    argparser.add_argument("testrunner", type=str, help="Test runner executable")
    argparser.add_argument("--chunks", type=int, help="Number of chunks", default=5)
    return argparser.parse_args()

def take(lst, n):
    return [ lst.pop() for i in xrange(n) ]

def chunkify(lst, chunks):
    lst = copy.copy(lst)
    chunk_size = len(lst) / chunks
    chunk_surplus = len(lst) % chunks

    result = [ take(lst, chunk_size) for i in xrange(chunks) ]
    if chunk_surplus:
        result.append(lst)

    return result

def build_processes(test_groups):
    processes = []
    for group in test_groups:
        cmd = (args.testrunner,) + tuple(group)
        processes.append((group,
                          subprocess.Popen(
                              cmd,
                              stdout=subprocess.PIPE,
                              stderr=subprocess.STDOUT,
                              preexec_fn=os.setpgrp)))
    return processes

args = parse_arguments()
test_suites = subprocess.check_output((args.testrunner, "--list")).strip().split("\n")
test_suite_groups = chunkify(test_suites, args.chunks)
processes = build_processes(test_suite_groups)
output = collections.defaultdict(str)

print "Running %d test suites in %d parallel chunks with ~%d tests each" % (len(test_suites), len(test_suite_groups), len(test_suite_groups[0]))

while True:
    prevlen = len(processes)
    for group, proc in processes:
        return_code = proc.poll()
        output[proc] += proc.stdout.read()

        if return_code == 0:
            processes.remove((group, proc))
            if not len(processes):
                print "All tests suites ran successfully"
                sys.exit(0)
        elif return_code is not None:
            print "One of '%s' test suites failed:" % ", ".join(group)
            print >>sys.stderr, output[proc]
            sys.exit(return_code)

        if prevlen != len(processes):
            prevlen = len(processes)
            print "%d test suite(s) left" % prevlen

        time.sleep(0.01)
