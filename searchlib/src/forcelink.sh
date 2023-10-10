#!/bin/sh
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

project=searchlib

if [ X$1 == "Xupdate" ]; then
    # update forcelink.hpp
    echo "generating forcelink.hpp..."
    guard=`pwd | sed -e "s|.*/${project}/||" -e "s|/|_|g"`
    prefix=forcelink_file_${project}_${guard}_
    echo "#ifndef GUARD_${project}_${guard}_FORCELINK" > forcelink.hpp
    echo "#define GUARD_${project}_${guard}_FORCELINK" >> forcelink.hpp
    echo "" >> forcelink.hpp
    find . -name "*.cpp" -maxdepth 1 | sed -e "s|.*/\(.*\)\.cpp|void ${prefix}\1();|" >> forcelink.hpp
    echo "" >> forcelink.hpp
    echo "void forcelink_${project}_${guard}() {" >> forcelink.hpp
    find . -name "*.cpp" -maxdepth 1 | sed -e "s|.*/\(.*\)\.cpp|    ${prefix}\1();|" >> forcelink.hpp
    echo "}" >> forcelink.hpp
    echo "" >> forcelink.hpp
    echo "#endif" >> forcelink.hpp
    echo "invoke 'forcelink_${project}_${guard}()' to force link this directory"

    # update .cpp files
    for file in *.cpp; do
	name=`echo "${prefix}${file}" | sed 's|\(.*\)\.cpp|\1|'`
        found=`grep ${name} ${file} | wc -l`
	if [ $found == "0" ]; then
	    echo "updating ${file}..."
	    echo ""                                 >> $file
	    echo "// this function was added by $0" >> $file
	    echo "void ${name}() {}"                >> $file
        fi
    done
else
    echo "This is a small utility script that might help out when trying to"
    echo "force the linkage of object files. When run in a subdirectory within"
    echo "${project}, it will create a 'forcelink.hpp' file that contains the"
    echo "force linkage wrapping code. It will also update any .cpp files in the"
    echo "directory with appropriate dummy functions to allow consistent force"
    echo "linkage. Note that this script will make a large"
    echo "number of assumptions; USE AT YOUR OWN RISK!"
    echo ""
    echo "if you feel lucky, run:"
    echo "$0 update"
fi
