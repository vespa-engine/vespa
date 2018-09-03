#!/bin/sh

DBGF=logs/vespa/debug.retention-enforcer
DBDIR=var/db/vespa/logfiledb
PIDF=$DBDIR/retention-enforcer.pid
RETAIN_DAYS=31

prereq_dir() {
	if [ -d $1 ] && [ -w $1 ]; then
		:
	else
		echo "$0: missing directory '$1' in '`pwd`'" >&2
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
	echo "Creating directory '$1' in '`pwd`'"
	mkdir -p $1 || exit 1
}

prepare_stuff() {
	check_prereqs
	exec > $DBGF.$$.log 2>&1
	ensure_dir $DBDIR
}

mark_pid() {
	echo $$ > $PIDF.$$.tmp
	mv $PIDF.$$.tmp $PIDF || exit 1
}

check_pidfile() {
	read pid < $PIDF
	[ "$pid" = $$ ] && return 0
	if [ "$pid" ] && [ $pid -gt $$ ]; then
		sleep 30
		read pid_again < $PIDF
		if [ "$pid_again" != "$pid" ]; then return 1; fi
		ps -p $pid >/dev/null 2>&1 || return 1
		proc=$(ps -p $pid 2>&1)
		case $proc in *retention*) ;; *) return 1;; esac
		echo "$0 [$$]: Yielding my place to pid '$pid'"
		exit 1
	fi
}

maybe_collect() {
	now=$(date +%s)
	chopnow=${now%?????}
	ts=${1##*/*.}
	[ "$ts" ] || return 1
	[ "$ts" -gt 0 ] || return 1
	add=$((3 * $RETAIN_DAYS))
	lim1=$(($ts + $add))
	mod_time=$(get_mode_time "$1")
	add=$((3 * 86400 * $RETAIN_DAYS))
	lim2=$(($mod_time + $add))
	if [ $lim1 -lt $chopnow ] && [ $lim2 -lt $now ]; then
		echo "Collect meta-logfile '$1' ts '$ts' (lim $lim, now $chopnow)"
		rm -f "$1"
	fi
}

get_mod_time() {
	perl -e 'print (((stat("'"$1"'"))[9]) . "\n")'
}

process_file() {
	now=$(date +%s)
	add=$((86400 * $RETAIN_DAYS))
	found=0
	while read timestamp logfilename; do
		if [ -f "$logfilename" ]; then
			found=1
			lim1=$(($timestamp + $add))
			mod_time=$(get_mod_time "$logfilename")
			lim2=$((mod_time + $add))
			if [ $lim1 -lt $now ] && [ $lim2 -lt $now ]; then
				echo "Collect logfile '$logfilename' timestamped $timestamp modified $mod_time"
				rm -f "$logfilename"
			fi
		fi
	done < $1
	if [ $found = 0 ]; then
		maybe_collect $1
	fi
}

process_all() {
	for dbf in $DBDIR/logfiles.* ; do
		[ -f "$dbf" ] && process_file "$dbf"
	done
}

mainloop() {
	while true; do
		mark_pid
		process_all
		sleep 3600
		check_pidfile
	done
}

prepare_stuff
mainloop
exit 0
