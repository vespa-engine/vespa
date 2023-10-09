# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
module="proton"

if [ $# -lt 1 ]; then
	echo "Code generation script for the $module module"
	echo ""
	echo "usage: $0 <class or interface name>"
	echo ""
	echo "Generates class files or interface files"
	echo "depending on which script was invoked."
	echo ""
	echo "The current directory is used to generate"
	echo "appropriate include guards."
	echo ""
	echo "Generated code is written to stdout."
	echo ""
	exit 1
fi

class=$1
name=`echo $class | tr 'A-Z' 'a-z'`
prefix=`pwd | sed -e "s|.*/${module}||" | tr '/' '_'`
guard=`echo H_${module}${prefix}_${class}_H | tr 'a-z' 'A-Z'`
ns_open="namespace $module {"
ns_close="} // namespace $module"

cat <<EOF
// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

EOF
