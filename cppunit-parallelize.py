#!/usr/bin/env python
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
# @author Vegard Sjonfjell
import sys
import argparse
import copy
import os
import subprocess
import time
import shlex

def parse_arguments():
    argparser = argparse.ArgumentParser(description="Run Vespa cppunit tests in parallell")
    argparser.add_argument("testrunner", type=str, help="Test runner executable")
    argparser.add_argument("--chunks", type=int, help="Number of chunks", default=5)
    args = argparser.parse_args()
    if args.chunks < 1:
        raise RuntimeError("Error: Chunk size must be greater than 0")

    return args

def take(lst, n):
    return [ lst.pop() for i in range(n) ]

def chunkify(lst, chunks):
    lst = copy.copy(lst)
    chunk_size = int(len(lst) / chunks)
    chunk_surplus = len(lst) % chunks

    result = [ take(lst, chunk_size) for i in range(chunks) ]
    if chunk_surplus:
        result.append(lst)

    return result

def error_if_file_not_found(function):
    def wrapper(*args, **kwargs):
        try:
            return function(*args, **kwargs)
        except OSError as e:
            if e.errno == os.errno.ENOENT: # "No such file or directory"
                print >>sys.stderr, "Error: could not find testrunner or valgrind executable"
                sys.exit(1)
    return wrapper

@error_if_file_not_found
def get_test_suites(testrunner):
    out = subprocess.check_output((testrunner, "--list"))
    return out.decode('utf-8').strip().split("\n")

class Process:
    def __init__(self, cmd, group):
        self.group = group
        self.finished = False
        self.output = ""
        self.handle = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            preexec_fn=os.setpgrp)

@error_if_file_not_found
def build_processes(test_groups):
    valgrind = os.getenv("VALGRIND")
    testrunner = shlex.split(valgrind) + [args.testrunner] if valgrind else [args.testrunner]
    processes = []

    for group in test_groups:
        cmd = testrunner + group
        processes.append(Process(cmd, group))

    return processes

def cleanup_processes(processes):
    for proc in processes:
        try:
            proc.handle.kill()
        except OSError as e:
            if e.errno != os.errno.ESRCH: # "No such process"
                print >>sys.stderr, e.message

args = parse_arguments()
test_suites = get_test_suites(args.testrunner)
test_suite_groups = chunkify(test_suites, args.chunks)
processes = build_processes(test_suite_groups)

print("Running %d test suites in %d parallel chunks with ~%d tests each" % (len(test_suites), len(test_suite_groups), len(test_suite_groups[0])))

processes_left = len(processes)
while True:
    try:
        for proc in processes:
            return_code = proc.handle.poll()
            proc.output += proc.handle.stdout.read().decode('utf-8')

            if return_code == 0:
                proc.finished = True
                processes_left -= 1
                if processes_left > 0:
                    print("%d test suite(s) left" % processes_left)
                else:
                    print("All test suites ran successfully")
                    sys.exit(0)
            elif return_code is not None:
                print("Error: one of '%s' test suites failed:" % ", ".join(proc.group))
                sys.stderr.write(proc.output)
                sys.exit(return_code)

            time.sleep(0.01)
    finally:
        cleanup_processes(processes)
