#!/bin/sh
# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

echo "generating Slime Mobile Edition ..."

# remove old version if present
rm -f slime_me.h
rm -f slime_me.cpp

# identify files to be included in the bundle, sort headers topologically based on include dependencies
hfiles=$(for file in *.h; do echo $file $file; cat $file | sed -n -e "s/#include \"\(.*\.h\)\"/\1 $file/p"; done | tsort)
cppfiles=$(echo *.cpp)

# bundle header files
echo "// Slime Mobile Edition"      >  slime_me.h
for file in $hfiles; do
    echo -e "\n// ---> FILE: $file" >> slime_me.h
    cat $file                       >> slime_me.h
    echo -e "// <--- FILE: $file\n" >> slime_me.h
done

# bundle cpp files
echo "// Slime Mobile Edition"      >  slime_me.cpp
echo "INCLUDE_SLIME_ME_H_HERE"      >> slime_me.cpp
for file in $cppfiles; do
    echo -e "\n// ---> FILE: $file" >> slime_me.cpp
    cat $file                       >> slime_me.cpp
    echo -e "// <--- FILE: $file\n" >> slime_me.cpp
done

# remove includes to local headers and print remaining includes
sed -i -e 's=#include "\(.*\)"=// include removed (\1)=' slime_me.h slime_me.cpp
echo -n "includes: "
cat slime_me.h slime_me.cpp | sed -n -e "s/#include \(.*\)/\1/p" | sort | uniq | xargs echo

# include slime_me.h from slime_me.cpp
sed -i -e 's=INCLUDE_SLIME_ME_H_HERE=#include "slime_me.h"=' slime_me.cpp

# move code from the vespalib namespace to the my namespace
sed -i -e 's/vespalib/my/g' slime_me.h slime_me.cpp
sed -i -e 's/VESPALIB/MY/g' slime_me.h slime_me.cpp

echo "running compilation tests ..."

# prepare temporary test directory
rm -rf tmp && mkdir tmp
cp slime_me.h slime_me.cpp tmp/

# simple single file application
cat > tmp/simple_app.cpp <<EOF
#include "slime_me.cpp"

int main() {
    my::Slime slime;
    return 0;
}
EOF
(cd tmp; set -x; make simple_app) || exit 11
(cd tmp; set -x; ./simple_app) || exit 12

# multiple includes from single compilation unit
cat > tmp/include_app.cpp <<EOF
#include "slime_me.h"
#include "include_app.h"

#include "slime_me.cpp"

int main() {
    my::Slime slime;
    return 0;
}
EOF
cat > tmp/include_app.h <<EOF
#ifndef INCLUDE_APP
#define INCLUDE_APP
#include "slime_me.h"
#endif
EOF
(cd tmp; set -x; make include_app) || exit 21
(cd tmp; set -x; ./include_app) || exit 22

# application with multiple compliation units
cat > tmp/multi_tools.h <<EOF
#ifndef MULTI_TOOLS_H
#define MULTI_TOOLS_H
struct MultiTools {
    int main();
};
#endif
EOF
cat > tmp/multi_tools.cpp <<EOF
#include "multi_tools.h"
#include "slime_me.h"

int
MultiTools::main()
{
    my::Slime slime;
    return 0;
}
EOF
cat > tmp/multi_app.cpp <<EOF
#include "slime_me.h"
#include "multi_tools.h"

int main() {
    MultiTools tools;
    return tools.main();
}
EOF
(cd tmp; set -x; g++ -c multi_tools.cpp) || exit 31
(cd tmp; set -x; g++ -c multi_app.cpp) || exit 32
(cd tmp; set -x; g++ -c slime_me.cpp) || exit 33
(cd tmp; set -x; g++ -o multi_app multi_tools.o multi_app.o slime_me.o) || exit 34
(cd tmp; set -x; ./multi_app) || exit 35

# clean up temporary test directory
rm -rf tmp

echo "AWESOME (slime_me.{h,cpp} are ready for use)"
