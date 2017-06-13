#!/bin/bash
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
set -e

rm -f new* chkpt*
sync
sleep 2

if ${VALGRIND} ./searchlib_fieldwriter_test_app "$@"
then
  :
else
  echo FAILURE: ./searchlib_fieldwriter_test_app program failed.
  exit 1
fi

checksame()
{
    file1=$1
    rval=0
    shift
    for file in $*
    do
      if cmp -s $file1 $file
      then
	  :
      else
	echo "FAILURE: $file1 != $file"
	rval=1
      fi
    done
    return $rval
}

newpcntfiles1=index/new[46]*dictionary.pdat
newpcntfiles1b=index/new[46]*dictionary.spdat
newpcntfiles1c=index/new[46]*dictionary.ssdat
newpcntfiles2=index/newskip[46]*dictionary.pdat
newpcntfiles2b=index/newskip[46]*dictionary.pdat
newpcntfiles2c=index/newskip[46]*dictionary.pdat
newpcntfiles3=index/newchunk[46]*dictionary.pdat
newpcntfiles3b=index/newchunk[46]*dictionary.pdat
newpcntfiles3c=index/newchunk[46]*dictionary.pdat
newpcntfiles4=index/new[57]*dictionary.pdat
newpcntfiles4b=index/new[57]*dictionary.pdat
newpcntfiles4c=index/new[57]*dictionary.pdat
newpcntfiles5=index/newskip[57]*dictionary.pdat
newpcntfiles5b=index/newskip[57]*dictionary.pdat
newpcntfiles5c=index/newskip[57]*dictionary.pdat
newpcntfiles6=index/newchunk[57]*dictionary.pdat
newpcntfiles6b=index/newchunk[57]*dictionary.pdat
newpcntfiles6c=index/newchunk[57]*dictionary.pdat
newpfiles1=index/new[46]*posocc.dat.compressed
newpfiles2=index/newskip[46]*posocc.dat.compressed
newpfiles3=index/newchunk[46]*posocc.dat.compressed
newpfiles4=index/new[57]*posocc.dat.compressed
newpfiles5=index/newskip[57]*posocc.dat.compressed
newpfiles6=index/newchunk[57]*posocc.dat.compressed

if checksame $newpcntfiles1 && checksame $newpcntfiles1b && checksame $newpcntfiles1c && checksame $newpfiles1 && checksame $newpcntfiles2 && checksame $newpcntfiles2b && checksame $newpcntfiles2c && checksame $newpfiles2 && checksame $newpcntfiles3 && checksame $newpcntfiles3b && checksame $newpcntfiles3c && checksame $newpfiles3 && checksame $newpcntfiles4 && checksame $newpcntfiles4b && checksame $newpcntfiles4c && checksame $newpfiles4 && checksame $newpcntfiles5 && checksame $newpcntfiles5b && checksame $newpcntfiles5c && checksame $newpfiles5 && checksame $newpcntfiles6 && checksame $newpcntfiles6b && checksame $newpcntfiles6c && checksame $newpfiles6
then
  echo SUCCESS: Files match up
  exit 0
else
  echo FAILURE: Files do not match up
  exit 1
fi
