#!/usr/bin/python
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
"""Usage: read.py [options] [vespa-fbench output file]

Will read from stdin if no file name is given

Wildcards:
    %d : any digits
     * : any string
     . : any char

Example:
    fbench-formatter.py file%d directory/file
    cat filename | fbench-formatter.py

Options:
    -h, --help              show this help
    -d, --dir=<string>      search directory [default: current directory]
    -n, --depth=<int>       search depth for subfolders [default: no limit]
    -f                      show file list
    
    -w                      give output as html
    -s                      give output as minimal tab seperated list
                            (headers is written to stderr)
    -c                      give output as comma seperated list
                            (headers is written to stderr)


    -t, --tag=<string>      set tag to output (use with -s)
"""
from math import sqrt
import os
import sys
import getopt
import re
from sets import Set

delimer = "[--xxyyzz--FBENCH_MAGIC_DELIMITER--zzyyxx--]"
urlFailStr = "FBENCH: URL FETCH FAILED!";
attributelist = ["NumHits", "NumFastHits", "TotalHitCount", "QueryHits", "QueryOffset", "NumErrors", "SearchTime", "AttributeFetchTime", "FillTime", "DocsSearched", "NodesSearched", "FullCoverage"]
timeAttributes = ['SearchTime', 'AttributeFetchTime', 'FillTime']


# Init
acc = {}
avg = {}
max_d = {}
min_d = {}

for i in attributelist:
    acc[i] = 0
    avg[i] = 0.0
    max_d[i] = 0
    min_d[i] = sys.maxint

entries = 0
fail   = 0

timeArray = list()
thisTime = 0
totalTime = 0

zeroHits = 0

# Global options
_filelist = 0
_output = 0
_dir = "."
_depth = 0

_tag = ""
_useTag = 0

def usage():
    print >> sys.stderr, __doc__

def abort(message):
    print >> sys.stderr, message + "\n"
    usage()
    sys.exit(2)

def main(argv):
    try:
        opts, args = getopt.getopt(argv, "h:d:n:t:fwsc", ["help", "dir=", "depth=", "tag="])
    except getopt.GetoptError:
        usage()
        sys.exit(2)

    global _output

    for opt, arg in opts:
        if opt in ("-h", "--help"):
            abort("")
        elif opt in ("-d", "--dir="):
            global _dir
            _dir = arg
        elif opt in ("-n", "--depth="):
            global _depth
            try:
                _depth = int(arg)
            except:
                abort("Depth must be an integer")
        elif opt == "-f":
            global _filelist
            _filelist = 1
        elif opt == "-w":
            _output = 1
        elif opt == "-s":
            _output = 2
        elif opt == "-c":
            _output = 3
        elif opt in ("-t", "--tag"):
            global _tag, _useTag
            _useTag = 1
            _tag = arg


    # Get file patterns
    files = Set()
    stdin = 1
    
    for argument in args:

        stdin = 0

        # Regex is translated into emacs-format
        filepattern = re.sub('[0-9]*%d', '[0-9]+', argument)

        # Get list of all matching files
        if (_depth == 0):
            cmd = "find %s -regex '.*/%s'" % (_dir, filepattern)
        else:
            cmd = "find %s -regex '.*/%s' -maxdepth %d" % (_dir, filepattern, _depth)
        fi = os.popen(cmd)

        list = fi.readlines()
        for i in list:
            files.add( i.strip() )
        if len(list) == 0:
            print >> sys.stderr, "\"%s\" does not match any files" % filepattern

    # Exit if no files or stdin
    if len(files) == 0 and stdin == 0:
        print >> sys.stderr, "No matching files found"
        sys.exit(1)

    # Print filenames
    if _filelist != 0:
        print "Files: "
        print files
        print ""

    # Print number of files
    if _filelist != 0:
        print >> sys.stderr, "Processing %d files..." % len(files)

    # Parse all files
    for file in files:
        parsefile(file)

    if stdin == 1:
        print >> sys.stderr, "Processing stdin..."
        parsefile("-")

    calculate()
    printResult()

def parsefile(filename):
    global zeroHits, entries, fail, timeArray, thisTime, acc, min_d, max_d

    if filename == "-":
        file = sys.stdin
    else:
        file = open(filename, "r")

    valid = 0

    for rawline in file:
        # Skip empty lines
        if (rawline == ""):
            continue
        
        line = rawline.strip()

        # Deliminer
        if (line == delimer):
            if valid == 1:
                entries += 1
                timeArray.append(thisTime)
                thisTime = 0
                valid = 0
                continue

        if (line == urlFailStr):
            fail += 1
            entries += 1
            continue

        # Split line at ':'
        match = line.split(':')
        if len(match) < 2:
            continue
        
        name = match[0].strip()
        valueStr = match[1].strip()

        if ( name in attributelist ):
            valid = 1
            print name

            # Extract info from header
            value = int(valueStr)
            acc[name] += value

            if (value == 0 and name == "TotalHitCount"):
                zeroHits += 1

            if (name in timeAttributes):
                thisTime += value

            # Find min/max
            if value < min_d[name]:
                min_d[name] = value

            if value > max_d[name]:
                max_d[name] = value

    file.close()

def calculate():

    global avg, avgTime, Sn, totalTime, timeArray

    successes = entries - fail

    # Calculate average values
    if successes == 0:
        print "Could not find any successfully runned queries"
        print "Make sure benchmarkdata reporting is activated"
        sys.exit(1);
    
    for entry in acc.keys():
        avg[entry] = float(acc[entry]) / successes

    # Calculate average total time
    totalTime = 0
    for i in timeAttributes:
        totalTime += acc[i]
    avgTime = float(totalTime) / float(successes)

    # Calculate standard deviation
    Sn = 0.0
    for sample in timeArray[1:]:
        Sn += ( float(sample)-avgTime )**2
    Sn = sqrt( Sn / successes )

def printResult():
    if _output == 0:
        printDefault()
    elif _output == 1:
        printHtml()
    elif _output == 2:
        printSimple()
    else:
        printCommaSeperated()

def printDefault():
    # Ordinary printing
    print "%21s\t%14s\t%10s\t%6s\t%6s" % ("NAME", "TOTAL", "AVG", "MIN", "MAX")
    for entry in acc.keys():
        print "%21s:\t%14d\t%10.2f\t%6d\t%6d" % (entry, acc[entry], avg[entry], min_d[entry], max_d[entry])
    print ""
    print "%21s:\t%14.3f\t%10.2f\t%6d\t%6d" % ( "Search+Fill+AttrFetch", totalTime, avgTime, min(timeArray), max(timeArray) )
    print "%21s:\t%14.3f" % ( "Standard deviation", Sn)
    print "%21s:\t%14d" % ( "Number of requests", entries)
    print "%21s:\t%14d" % ( "successful requests", entries-fail)
    print "%21s:\t%14d" % ( "failed requests", fail)

    print "%21s:\t%14d" % ( "zero hit requests", zeroHits)

def printHtml():
    
        # HTML printing
        print "<html>"
        print "  <head>"
        print "    <title=\"Fbench\">"
        print "  </head>"
        print "  <body>"
        
        print "    <table>"
        print "      <tr>"
        print "        <th align='left'>Name</th>"
        print "        <th>Total</th>"
        print "        <th>Avg</th>"
        print "        <th>Min</th>"
        print "        <th>Max</th>"
        print "      </tr>"
        for entry in acc.keys():
            print "      <tr>"
            print "        <td>%s</td>" % entry
            print "        <td align='right'>%d</td>" % acc[entry]
            print "        <td align='right'>%.2f</td>" % avg[entry]
            print "        <td align='right'>%d</td>" % min_d[entry]
            print "        <td align='right'>%d</td>" % max_d[entry]
            print "      </tr>"
        print "    </table>"

        print "    <table>"
        print "      <tr>"
        print "        <th align='left'>Average time</th>"
        print "        <td align='right'>%.3f ms </td>" % avgTime
        print "      </tr>"
        print "        <th align='left'>Standard deviation</th>"
        print "        <td align='right'>%.3f</td>" % Sn
        print "      </tr>"
        print "      </tr>"
        print "        <th align='left'>Number of requests</th>"
        print "        <td align='right'>%d</td>" % entries
        print "      </tr>"
        print "      </tr>"
        print "        <th align='left'>Number of successful requests</th>"
        print "        <td align='right'>%d</td>" % entries - fail
        print "      </tr>"
        print "      </tr>"
        print "        <th align='left'>Number of failed requests</th>"
        print "        <td align='right'>%d</td>" % fail
        print "      </tr>"
        print "      </tr>"
        print "        <th align='left'>Number of zero hit requests</th>"
        print "        <td align='right'>%d</td>" % zeroHits
        print "      </tr>"
        print "    </table>"
        print "  </body>"

def printSimple():
    # Minimal print
    printHeader = ""
    for entry in acc.keys():
        printHeader += entry + '\t'
    printHeader += "NumRequests\t"
    printHeader += "NumSuccess\t"
    printHeader += "NumFailed\t"
    printHeader += "ZeroHitRequests\t"
    printHeader += "TotalTime\t"
    if _useTag:
        printHeader += "Tag"
    print >> sys.stderr, printHeader
        
    printtext = ""
    for entry in acc.keys():
        printtext += str(acc[entry]) + '\t'
    printtext += str(entries) + '\t'
    printtext += str(entries-fail) + '\t'
    printtext += str(fail) + '\t'
    printtext += str(zeroHits) + '\t'
    printtext += str(totalTime) + '\t'
    if _useTag:
        printtext += _tag
    print printtext

def printCommaSeperated():
    printHeader = ""
    for entry in acc.keys():
        printHeader += entry + ','
    printHeader += "NumRequests,"
    printHeader += "NumSuccess,"
    printHeader += "NumFailed,"
    printHeader += "ZeroHitRequests,"
    if _useTag:
        printHeader += "TotalTime,"
        printHeader += "Tag"
    else:
        printHeader += "TotalTime"
    print >> sys.stderr, printHeader
        
    printtext = ""
    for entry in acc.keys():
        printtext += str(acc[entry]) + ','
    printtext += str(entries) + ','
    printtext += str(entries-fail) + ','
    printtext += str(fail) + ','
    printtext += str(zeroHits) + ','
    if _useTag:
        printtext += str(totalTime) + ','
        printtext += _tag
    else:
        printtext += str(totalTime)
    print printtext

if __name__ == "__main__":
    main(sys.argv[1:])
