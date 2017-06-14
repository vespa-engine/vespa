#!/bin/sh
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

( cat << "EOF"
temp/id                                           src/test/derived/id
temp/mlr                                          src/test/derived/mlr
temp/mail                                         src/test/derived/mail
temp/local                                        src/test/derived/local
temp/music                                        src/test/derived/music
temp/types                                        src/test/derived/types
temp/arrays                                       src/test/derived/arrays
temp/flickr                                       src/test/derived/flickr
temp/mother                                       src/test/derived/inheritance/mother
temp/music3                                       src/test/derived/music3
temp/complex                                      src/test/derived/complex
temp/deriver                                      src/test/derived/deriver
temp/mail4_1                                      src/test/derived/mail4_1
temp/newrank                                      src/test/derived/newrank
temp/sorting                                      src/test/derived/sorting
temp/advanced                                     src/test/derived/advanced
temp/ranktypes                                    src/test/derived/ranktypes
temp/attributes                                   src/test/derived/attributes
temp/emptychild                                   src/test/derived/emptychild
temp/exactmatch                                   src/test/derived/exactmatch
temp/indexschema                                  src/test/derived/indexschema
temp/inheritance                                  src/test/derived/inheritance
temp/emptydefault                                 src/test/derived/emptydefault
temp/rankprofiles                                 src/test/derived/rankprofiles
temp/attributerank                                src/test/derived/attributerank
temp/indexsettings                                src/test/derived/indexsettings
temp/indexswitches                                src/test/derived/indexswitches
temp/rankexpression                               src/test/derived/rankexpression
temp/rankproperties                               src/test/derived/rankproperties
temp/structanyorder                               src/test/derived/structanyorder
temp/documentderiver                              src/test/derived/documentderiver
temp/streamingstruct                              src/test/derived/streamingstruct
temp/annotationssimple                            src/test/derived/annotationssimple
temp/attributeprefetch                            src/test/derived/attributeprefetch
temp/multiplesummaries                            src/test/derived/multiplesummaries
temp/inheritancebadtypes                          src/test/derived/inheritancebadtypes
temp/twostreamingstructs                          src/test/derived/twostreamingstructs
temp/annotationsreference                         src/test/derived/annotationsreference
temp/prefixexactattribute                         src/test/derived/prefixexactattribute
temp/annotationspolymorphy                        src/test/derived/annotationspolymorphy
temp/annotationsinheritance                       src/test/derived/annotationsinheritance
temp/streamingstructdefault                       src/test/derived/streamingstructdefault
temp/annotationsinheritance2                      src/test/derived/annotationsinheritance2
temp/annotationsimplicitstruct                    src/test/derived/annotationsimplicitstruct
temp/integerattributetostringindex                src/test/derived/integerattributetostringindex
temp/combinedattributeandindexsearch              src/test/derived/combinedattributeandindexsearch
tmp/v2/complex/search/cluster.music/tlds/tld.0                src/test/cfg/search/compare/complex/search/cluster.music/tlds/tld.0
tmp/v2/complex/search/cluster.music/tlds/tld.1                src/test/cfg/search/compare/complex/search/cluster.music/tlds/tld.1
tmp/v2/complex/search/cluster.rt/tlds/tld.0                   src/test/cfg/search/compare/complex/search/cluster.rt/tlds/tld.0
tmp/v2/optionals/search/cluster.music/tlds/tld.0              src/test/cfg/search/compare/optionals/search/cluster.music/tlds/tld.0
tmp/v2/simple/search/cluster.music/tlds/tld.0                 src/test/cfg/search/compare/simple/search/cluster.music/tlds/tld.0
tmp/v2/twoFeedTargetClusters/search/cluster.music1/tlds/tld.0 src/test/cfg/search/compare/twoFeedTargetClusters/search/cluster.music1/tlds/tld.0
tmp/v2/twoFeedTargetClusters/search/cluster.music2/tlds/tld.0 src/test/cfg/search/compare/twoFeedTargetClusters/search/cluster.music2/tlds/tld.0
EOF
) | while read from to ; do
	echo check fromdir $from todir $to 1>&2
        test -d $from || echo missing $from 1>&2
        test -d $to || echo missing $to 1>&2
	for fromfile in $from/*cfg ; do
		base=${fromfile##*/}
		base=${base%%.*}
		tofile=`ls $to/${base}.*cfg 2>/dev/null`
		if [ "$tofile" ] && [ -f "$tofile" ]; then
			cmp -s $fromfile $tofile || echo cp $fromfile $tofile
		fi
	done
done | sh -x
