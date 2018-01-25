#!/bin/bash
# Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

set -e

Usage() {
    cat <<EOF
Usage: ${0##*/} [OPTIONS]...SPECFILE
Run rpmbuild with the given specfile macros, creating TOPDIR if necessary.

Options:
  -b BUILDDIR  Overrides %_builddir.
  -d DIST      The %dist to build for (e.g. .el7 for RHEL 7). Can be specified
               multiple time to build multiple RPMs. The default %dist is used
               if no -d options have been specified.
  -h           Print this help text and exit.
  -t TOPDIR    Overrides %_topdir.
  -v VERSION   [Required] The version of the RPM.
EOF

    exit 1
}

Fail() {
    printf "%s\n" "$*"
    exit 1
}

Run() {
    local command="$1"
    shift
    printf "%q" "$command"

    local arg
    for arg in "$@"; do
	printf " %q" "$arg"
    done
    printf "\n"

    "$command" "$@"
}

Main() {
    local -a dists=()
    local version= topdir= builddir=
    while (( $# > 0 )); do
	case "$1" in
	    -b|--builddir)
		builddir="$2"
		shift 2
		if ! test -d "$builddir"; then
		    Fail "BUILDDIR '$builddir' does not exist"
		# Make builddir an absolute path
		elif ! builddir=$(readlink -e "$builddir"); then
		    Fail "Failed to resolve BUILDDIR '$builddir'"
		fi
		;;
	    -d|--dist)
		local dist="$2"
		shift 2
		case "$dist" in
		    .el6|.el7) : ;;
		    *) Fail "Bad DIST value '$dist'" ;;
		esac
		dists+=("$dist")
		;;
	    -t|--topdir)
		topdir="$2"
		shift 2
		if ! [[ "$topdir" =~ ^/ ]]; then
		    Fail "TOPDIR must be an absolute path"
		fi
		;;
	    -v|--version)
		version="$2"
		shift 2
		if ! [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
		    Fail "VERSION must be a version of the form X.Y.Z"
		fi
		;;
	    *) break ;;
	esac
    done

    if (( $# == 0 )); then
	Fail "Missing SPECFILE"
    elif (( $# > 1 )); then
	Fail "Too many arguments"
    else
	case "$1" in
	    help|-h|--help) Usage ;;
	esac
    fi
    local specfile="$1"

    local -a defines=()

    if test -n "$builddir"; then
	defines+=(--define "_builddir $builddir")
    fi

    if test -n "$topdir"; then
	if ! mkdir -p "$topdir"; then
	    Fail "Failed to create TOPDIR directory '$topdir'"
	fi
	defines+=(--define "_topdir $topdir")
    fi

    if test -n "$version"; then
	defines+=(--define "version $version")
    else
	Fail "VERSION is required"
    fi

    if (( ${#dists[@]} == 0 )); then
	Run rpmbuild -bb "${defines[@]}" "$specfile"
    else
	local dist
	for dist in "${dists[@]}"; do
	    Run rpmbuild -bb "${defines[@]}" --define "dist $dist" "$specfile"
	done
    fi
}

Main "$@"
