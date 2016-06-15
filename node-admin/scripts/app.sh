#!/bin/bash
# Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
        # ensure it ends with "/" :
        VESPA_HOME=${VESPA_HOME%/}/
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

set -e

source "${0%/*}"/common.sh

declare SCRIPTS_DIR="${0%/*}"

declare -r APP_DIR_NAME_UNDER_SHARED=app

function Usage {
    UsageHelper "$@" <<EOF
Usage: $SCRIPT_NAME <command> [<app-dir>]
Deploy (or undeploy) application rooted at <app-dir> on localhost Config Server.

The local zone must be up and running. <app-dir> should point to
e.g. vespa/basic-search-on-docker/target/application.
EOF
}

function RunOnConfigServer {
    docker exec config-server "$@"
}

function VerifyApp {
    local app_dir="$1"

    # Sanity-check app_dir
    if ! [ -d "$app_dir" ]
    then
        Fail "<app-dir> '$app_dir' is not a directory"
    fi

    local services_xml="$app_dir"/services.xml
    if ! [ -f "$services_xml" ]
    then
        Fail "Failed to find services.xml in <app-dir> '$app_dir'"
    fi

    # Verify there's no <admin> element.
    if grep -qE '<admin[ >]' "$services_xml"
    then
        Fail "services.xml cannot contain an <admin> element in hosted Vespa"
    fi

    # Verify <nodes> seems to be correctly specified (warning: this test is
    # incomplete).
    if grep -qE "<nodes>" "$services_xml" ||
            ! grep -qE "<nodes (.* )?docker-image=" "$services_xml" ||
            ! grep -qE "<nodes (.* )?flavor=[\"']docker[\"']" "$services_xml"
    then
        Fail "You must specify the <nodes> element in the following form" \
             "in hosted Vespa w/Docker:" \
             "  <nodes count=\"2\" flavor=\"docker\" docker-image=\"IMAGE\" />" \
             "where IMAGE is e.g. vespa-local:latest."
    fi
}

# Copies the application rooted at $1 to a directory tree shared with the
# Config Server.
function CopyToSharedDir {
    local app_dir="$1"

    local shared_dir_on_localhost="$APPLICATION_STORAGE_ROOT/$CONFIG_SERVER_CONTAINER_NAME/$ROOT_DIR_SHARED_WITH_HOST"
    if ! [ -d "$shared_dir_on_localhost" ]
    then
        Fail "Failed to find the Config Server's shared directory on" \
             "localhost '$shared_dir_on_localhost', has the" \
             "$CONFIG_SERVER_CONTAINER_NAME container been started?"
    fi


    local shared_app_dir_on_localhost="$shared_dir_on_localhost/$APP_DIR_NAME_UNDER_SHARED"
    if [ "$shared_app_dir_on_localhost" != /home/docker/container-storage/config-server/shared/app ]
    then
        # This duplication of code is a safety-guard against 'rm -rf' unknown
        # directories.
        Fail "We're about to remove '$shared_app_dir_on_localhost', but it's" \
             "pointing to something unexpected, refusing to proceed..."
    fi

    echo -n "Copying application to '$shared_app_dir_on_localhost'... "
    rm -rf "$shared_app_dir_on_localhost"
    cp -r "$app_dir" "$shared_app_dir_on_localhost"
    echo done
}

function DeployApp {
    if (($# != 1))
    then
        Usage
    fi

    local app_dir="$1"

    VerifyApp "$app_dir"

    CopyToSharedDir "$app_dir"

    # Create tenant
    echo -n "Creating tenant... "
    local create_tenant_response
    if create_tenant_response=$(curl --silent --show-error -X PUT "http://$CONFIG_SERVER_HOSTNAME:$CONFIG_SERVER_PORT/application/v2/tenant/$TENANT_NAME" 2>&1)
    then
        if ! [[ "$create_tenant_response" =~ "Tenant $TENANT_NAME created" ]] &&
                ! [[ "$create_tenant_response" =~ "already exists" ]]
        then
            echo
            Fail "May have failed to create the tenant: '$create_tenant_response'"
        fi
    else
        echo
        Fail "Failed to create the tenant: $?: '$create_tenant_response'"
    fi
    echo done

    # Deploy app
    local app_dir_on_config_server="/$ROOT_DIR_SHARED_WITH_HOST/$APP_DIR_NAME_UNDER_SHARED"
    RunOnConfigServer $VESPA_HOME/bin/deploy -e "$TENANT_NAME" prepare "$app_dir_on_config_server"
    echo "Activating application"
    RunOnConfigServer $VESPA_HOME/bin/deploy -e "$TENANT_NAME" activate
}

function UndeployApp {
    if (($# != 0))
    then
        Usage "undeploy takes no arguments"
    fi

    local app_name=default
    local output
    echo -n "Removing application $TENANT_NAME:$app_name... "
    if ! output=$(curl --silent --show-error -X DELETE "http://$CONFIG_SERVER_HOSTNAME:$CONFIG_SERVER_PORT/application/v2/tenant/$TENANT_NAME/application/$app_name")
    then
        echo
        Fail "Failed to remove application: $output"
    fi

    echo done
}

function Main {
    if (($# == 0))
    then
        Usage "Missing command"
    fi
    local command="$1"
    shift

    case "$command" in
        deploy) DeployApp "$@" ;;
        undeploy) UndeployApp "$@" ;;
        *) Usage "Unknown command '$command'" ;;
    esac
}

Main "$@"
