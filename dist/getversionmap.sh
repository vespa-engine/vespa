#!/bin/sh
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

srcdir=$1

[ "$srcdir" ] && [ -d "$srcdir" ] && [ -f "$srcdir/VERSION" ] || { echo "Usage: $0 <source-top-dir>" >&2; exit 1; }

cd "$srcdir"

dateadd=${VBUILD_VERSION_DATE}
if [ "$dateadd" = "" ]; then
    dateadd=$(date +"%Y%m%d.%H%M%S")
fi

tag="HEAD"

version=${FACTORY_VESPA_VERSION}
if [ "$version" = "" ]; then
    mainver=$(cat VERSION)
    version="${mainver}.${dateadd}"
fi

ostype=$(uname -s)
osver=$(uname -r)
osarch=$(uname -m)
commit_sha=$(git rev-parse HEAD || echo ffffffffffffffffffffffffffffffffffffffff)
commit_date=$(git show -s --format=%ct ${commit_sha} || echo 0)

vtag_system_rev="${ostype}-${osver}"
who=$(whoami || logname)
where=$(uname -n)
where=${where%.yahoo.com}

vtag_date=${dateadd}

mv=$version
major=${mv%%.*}; mv=${mv#*.}
minor=${mv%%.*}; mv=${mv#*.}
micro=${mv%%.*};

if [ "$major" = "" ]; then major=0; fi
if [ "$minor" = "" ]; then minor=0; fi
if [ "$micro" = "" ]; then micro=0; fi

vtag_component=$major.$minor.$micro

echo "V_TAG             ${tag}"
echo "V_TAG_DATE        ${vtag_date}"
echo "V_TAG_PKG         ${version}"
echo "V_TAG_ARCH        ${osarch}"
echo "V_TAG_SYSTEM      ${ostype}"
echo "V_TAG_SYSTEM_REV  ${vtag_system_rev}"
echo "V_TAG_BUILDER     ${who}@${where}"
echo "V_TAG_COMPONENT   ${vtag_component}"
echo "V_TAG_COMMIT_SHA  ${commit_sha}"
echo "V_TAG_COMMIT_DATE ${commit_date}"

exit 0
