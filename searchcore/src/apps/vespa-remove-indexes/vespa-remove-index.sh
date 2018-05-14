#!/bin/sh
# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# BEGIN environment bootstrap section
# Do not edit between here and END as this section should stay identical in all scripts

findpath () {
    myname=${0}
    mypath=${myname%/*}
    myname=${myname##*/}
    if [ "$mypath" ] && [ -d "$mypath" ]; then
        return
    fi
    mypath=$(pwd)
    if [ -f "${mypath}/${myname}" ]; then
        return
    fi
    echo "FATAL: Could not figure out the path where $myname lives from $0"
    exit 1
}

COMMON_ENV=libexec/vespa/common-env.sh

source_common_env () {
    if [ "$VESPA_HOME" ] && [ -d "$VESPA_HOME" ]; then
        export VESPA_HOME
        common_env=$VESPA_HOME/$COMMON_ENV
        if [ -f "$common_env" ]; then
            . $common_env
            return
        fi
    fi
    return 1
}

findroot () {
    source_common_env && return
    if [ "$VESPA_HOME" ]; then
        echo "FATAL: bad VESPA_HOME value '$VESPA_HOME'"
        exit 1
    fi
    if [ "$ROOT" ] && [ -d "$ROOT" ]; then
        VESPA_HOME="$ROOT"
        source_common_env && return
    fi
    findpath
    while [ "$mypath" ]; do
        VESPA_HOME=${mypath}
        source_common_env && return
        mypath=${mypath%/*}
    done
    echo "FATAL: missing VESPA_HOME environment variable"
    echo "Could not locate $COMMON_ENV anywhere"
    exit 1
}

findroot

# END environment bootstrap section

ROOT=${VESPA_HOME%/}
cd $ROOT || { echo "Cannot cd to $ROOT" 1>&2; exit 1; }

usage() {
    (
	echo "This script will remove vespa indexes on this node."
        echo "It will refuse to execute if Vespa is running."
        echo "The following options are recognized:"
        echo ""
        echo "-cluster <cluster>      only remove data for given cluster"
        echo "-key <distribution key> only remove data for given key"
        echo "-row <row number>       only remove data for given row"
        echo "-column <col number>    only remove data for given column"
        echo "-old                    remove data from Vespa 4.2 or older"
        echo "-force                  do not ask for confirmation before removal"
    ) >&2
}

onlycluster=""
onlykey=""
onlyrow=""
onlycolumn=""
sudo="sudo"
ask=true
removeold=false
confirmed=true
olddir=var/db/vespa/index
newdir=var/db/vespa/search

if [ -w $newdir ] && [ -w $olddir ]; then
    sudo=""
fi

while [ $# -gt 0 ]; do
    case $1 in
        -h|-help)        usage; exit 0;;
        -C|-cluster)     shift; onlycluster="$1"; shift ;;
        -k|-key)         shift; onlykey="$1"; shift ;;
        -r|-row)         shift; onlyrow="$1"; shift ;;
        -c|-column)      shift; onlycolumn="$1" ; shift ;;
        -nosudo)         shift; sudo="" ;;
        -sudo)           shift; sudo="sudo" ;;
        -old)            shift; removeold=true ;;
	-force)          shift; ask=false ;;
        *)               echo "Unrecognized option '$1'" >&2; usage; exit 1;;
    esac
done
# Will first check if vespa is running on this node
P_CONFIGPROXY=var/run/configproxy.pid
if [ -f $P_CONFIGPROXY ] && $sudo kill -0 `cat $P_CONFIGPROXY` 2>/dev/null; then
  echo "[ERROR] Will not remove indexes while Vespa is running" 1>&2
  echo "[ERROR] stop services and run 'ps xgauww' to check for Vespa processes" 1>&2
  exit 1
fi

removedata() {
    echo "[info] removing data: $sudo rm -rf $*"
    $sudo rm -rf $*
    echo "[info] removed."
}

confirm() {
    confirmed=false
    echo -n 'Really to remove this vespa index? Type "yes" if you are sure ==> ' 1>&2
    answer=no
    read answer
    if [ "$answer" = "yes" ]; then
	confirmed=true
    else
        confirmed=false
        echo "[info] skipping removal ('$answer' != 'yes')"
    fi
}

garbage_collect_dirs() {
    find $olddir $newdir -type d -depth 2>/dev/null | while read dir; do
	[ "$dir" = "$olddir" ] && continue
	[ "$dir" = "$newdir" ] && continue
	$sudo rmdir "$dir" 2>/dev/null
    done
}

garbage_collect_dirs

if $removeold && [ -d $olddir ]; then
    if $ask; then
	kb=$(du -sk $olddir | awk '{print $1}')
	if [ $kb -gt 100 ]; then
	    confirm
	fi
    fi
    if $confirmed; then
	removedata $olddir/*
    fi
fi

dokey() {
    key=$1
    keydir=$clusterdir/n$key
    if ! [ -e "$keydir" ]; then
        echo "directory '$keydir' does not exist"
        return
    fi
    kb=$(du -sk $keydir | awk '{print $1}')

    echo "[info] For cluster $cluster distribution key $key you have:"
    echo "[info] $kb kilobytes of data in $keydir"
    if $ask && [ "$kb" -gt 2 ]; then
	confirm
    fi
    if $confirmed; then
	removedata $keydir
    fi
    echo ""
}

docol() {
    column=$1
    coldir=$rowdir/c$column
    if ! [ -e "$coldir" ]; then
        echo "directory '$coldir' does not exist"
        return
    fi
    kb=$(du -sk $coldir | awk '{print $1}')

    echo "[info] For cluster $cluster row $row column $column you have:"
    echo "[info] $kb kilobytes of data in $coldir"
    if $ask && [ "$kb" -gt 2 ]; then
	confirm
    fi
    if $confirmed; then
	removedata $coldir
    fi
    echo ""
}

dorow() {
    row=$1
    rowdir=$clusterdir/r$row
    if [ "$onlycolumn" ]; then
	docol $onlycolumn
    else
	for c in `ls $rowdir`; do
	    col=${c#c}
	    if [ "$col" ] && [ "$col" != "$c" ]; then
		docol $col
	    fi
	done
    fi
}

docluster() {
    cluster=$1
    clusterdir=$newdir/cluster.$cluster
    if ! [ -e "$clusterdir" ]; then
        echo "Skip $cluster cluster: directory '$clusterdir' does not exist"
        return
    fi
    kb=$(du -sk $clusterdir | awk '{print $1}')
    if [ $kb -gt 1000 ]; then
	echo "[info] You have $kb kilobytes of data for cluster $cluster"
    fi
    if [ "$onlykey" ]; then
        dokey $onlykey
    elif [ "$onlyrow" ]; then
	dorow $onlyrow
    else
	for dirname in `ls $clusterdir`; do
	    key=${dirname#n}
	    if [ "$key" ] && [ "$key" != "$dirname" ]; then
		dokey $key
	    fi
	    row=${dirname#r}
	    if [ "$row" ] && [ "$row" != "$dirname" ]; then
		dorow $row
	    fi
	done
    fi
}

if [ "$onlycluster" ]; then
    docluster $onlycluster
else
    for dir in $newdir/cluster.* ; do
	[ -d $dir ] || continue
	cluster=${dir#*/cluster.}
	docluster $cluster
    done
fi

garbage_collect_dirs

exit 0
