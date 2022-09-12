#!/bin/sh
# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

# daemon that collects old log files.
# global settings:

DBGF=logs/vespa/debug.retention-enforcer
DBDIR=var/db/vespa/logfiledb
PIDF=$DBDIR/retention-enforcer.pid
RETAIN_DAYS=30

# this depends on components adding their log files
# to a "database" in DBDIR named "logfiles.TTTTT" where
# TTTTT is a timestamp in format (seconds/100000).
# The "database" holds lines with format "timestamp /path/to/logfile"
# where "timestamp" is just seconds since epoch.

log_msg() {
	echo "$0 [$$]" "$@"
}

prereq_dir() {
	if [ -d $1 ] && [ -w $1 ]; then
		:
	else
		log_msg ERROR "missing directory '$1' in '`pwd`'" >&2
		exit 1
	fi
}

check_prereqs() {
	prereq_dir var/db/vespa
	prereq_dir logs/vespa
}

ensure_dir () {
	if [ -d $1 ] && [ -w $1 ]; then
		return 0
	fi
	log_msg INFO "Creating directory '$1' in '`pwd`'"
	mkdir -p $1 || exit 1
}

prepare_stuff() {
	check_prereqs
	ensure_dir ${DBGF%/*}
	exec >> $DBGF.log 2>&1
	ensure_dir $DBDIR
}

bad_timestamp() {
	now=$(date +%s)
	if [ "$1" ] && [ "$1" -ge 1514764800 ] && [ "$1" -le $now ]; then
		# sane timestamp:
		return 1
	fi
	# bad timestamp:
	return 0
}

mark_pid() {
	echo $$ > $PIDF.$$.tmp
	mv $PIDF.$$.tmp $PIDF || exit 1
}

check_pidfile() {
	[ -f $PIDF ] || return 0
	read pid < $PIDF
	[ "$pid" = $$ ] && return 0
	if [ "$pid" ] && [ $pid -gt $$ ]; then
		sleep 30
		read pid_again < $PIDF
		if [ "$pid_again" != "$pid" ]; then return 1; fi
		ps -p $pid >/dev/null 2>&1 || return 1
		proc=$(ps -p $pid 2>&1)
		case $proc in *retention*) ;; *) return 1;; esac
		log_msg INFO "Yielding my place to pid '$pid'"
		exit 1
	fi
}

get_mod_time() {
	stat -c %Y -- "$1"
}

maybe_collect() {
	timestamp=$1
	logfilename=$2

	if bad_timestamp "$1"; then
		log_msg WARNING "bad timestamp '$timestamp' for logfilename '$logfilename'"
		return
	fi

	add=$((86400 * $RETAIN_DAYS))
	lim1=$(($timestamp + $add))
	mod_time=$(get_mod_time "$logfilename")
	lim2=$(($mod_time + $add))

	if [ $lim1 -lt $now ] && [ $lim2 -lt $now ]; then
		log_msg INFO "Collect logfile '$logfilename' timestamped $timestamp modified $mod_time"
		rm -f "$logfilename"
	fi
}

process_file() {
	dbfile="$1"
	now=$(date +%s)
	dbf_ts_prefix=${dbfile##*.}
	dbf_ts_beg=${dbf_ts_prefix}00000
	dbf_ts_end=${dbf_ts_prefix}99999
	add=$((86400 * $RETAIN_DAYS))
	earliest_expire=$((${dbf_ts_beg} + $add))
	if [ $earliest_expire -gt $now ]; then
		return 0
	fi
	found=0
	while read timestamp logfilename; do
		sleep 1
		for fn in $logfilename $logfilename.*z*; do
			if [ -f "$fn" ]; then
				found=1
				maybe_collect "$timestamp" "$fn"
			fi
		done
	done < $dbfile
	if [ $found = 0 ]; then
		maybe_collect "${dbf_ts_end}" "$dbfile"
	fi
}

process_all() {
	for dbf in $DBDIR/logfiles.* ; do
		[ -f "$dbf" ] || continue
		process_file "$dbf"
		sleep 1
	done
}

mainloop() {
	while true; do
		check_pidfile
		mark_pid
		process_all
		sleep 3600
	done
}

# MAIN:

prepare_stuff
sleep 600
mainloop
exit 0
