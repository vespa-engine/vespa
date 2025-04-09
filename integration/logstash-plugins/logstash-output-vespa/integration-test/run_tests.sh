#!/bin/bash

# Configuration
LOGSTASH_HOME="/opt/logstash/logstash-current"
VESPA_URL="http://localhost:8080"
PLUGIN_VERSION=$(cat ../VERSION)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
ORANGE='\033[0;33m'
NC='\033[0m'

# Test result counters
test_count=0
failed_count=0

command_exists() {
    command -v "$1" >/dev/null 2>&1
}

#### Check prerequisites

# Logstash
if [ ! -d "$LOGSTASH_HOME" ]; then
    echo -e "${RED}Error: Logstash not found at $LOGSTASH_HOME${NC}"
    exit 1
fi

# curl
if ! command_exists curl; then
    echo -e "${RED}Error: curl is required but not installed${NC}"
    exit 1
fi

# podman
if ! command_exists podman; then
    echo -e "${RED}Error: podman is required but not installed${NC}"
    exit 1
fi

# netcat (nc) for port checking
if ! command_exists nc; then
    echo -e "${RED}Error: netcat (nc) is required but not installed${NC}"
    exit 1
fi

# Check if ports required by Vespa are already in use
if nc -z localhost 8080 >/dev/null 2>&1; then
    echo -e "${RED}Error: Port 8080 is already in use${NC}"
    exit 1
fi

if nc -z localhost 19071 >/dev/null 2>&1; then
    echo -e "${RED}Error: Port 19071 is already in use${NC}"
    exit 1
fi

# TODO is port 8080 or 19071 already in use?

### Build and install plugin
echo -e "${ORANGE}Building plugin...${NC}"
cd ..
./gradlew gem
# bail if the plugin is not built
if [ $? -ne 0 ]; then
    echo -e "${RED}Error: Plugin not built${NC}"
    exit 1
fi

cd integration-test
echo -e "${ORANGE}Installing plugin...${NC}"
$LOGSTASH_HOME/bin/logstash-plugin install --no-verify ../logstash-output-vespa_feed-$PLUGIN_VERSION.gem
# bail if the plugin is not installed
if [ $? -ne 0 ]; then
    echo -e "${RED}Error: Plugin not installed${NC}"
    exit 1
fi

#### Start Vespa
echo -e "${ORANGE}Starting Vespa...${NC}"
podman run --detach --name vespa-logstash-test --hostname vespa-logstash-test --publish 8080:8080 --publish 19071:19071 vespaengine/vespa

#### Functions
run_test() {
    local test_name=$1
    local test_description=$2
    local input=$3
    local expected_doc_id=$4
    local expected_status=$5

    test_count=$((test_count + 1))
    
    echo -e "${ORANGE}Test: $test_description${NC}"
    echo "$input" | $LOGSTASH_HOME/bin/logstash -f $(pwd)/config/logstash_output_test_${test_name}.conf &
    logstash_pid=$!

    # Wait for Logstash to start by pinging its monitoring endpoint
    echo -e "${ORANGE}Waiting for Logstash to start...${NC}"
    DONE_ALREADY=false
    while ! curl -s -o /dev/null --fail "localhost:9600/_node/stats"; do
        sleep 1
        if ! kill -0 $logstash_pid 2>/dev/null; then
            echo -e "${ORANGE}Logstash already done and exited, moving on...${NC}"
            DONE_ALREADY=true
            break
        fi
    done
    
    if [ "$DONE_ALREADY" = false ]; then
        echo -e "${ORANGE}Giving Logstash time to process...${NC}"
        if [ "$test_name" = "deploy_local_package" ]; then
            echo -e "${ORANGE}Waiting more because we deployed a package...${NC}"
            sleep 10
        else
            sleep 2
        fi
        echo -e "${ORANGE}Killing Logstash...${NC}"
        kill $logstash_pid
        echo -e "${ORANGE}Waiting for Logstash to exit...${NC}"
        wait $logstash_pid
    fi

    # if we don't have an expected doc ID, we deployed the package and we want for Vespa to be ready
    if [ -z "$expected_doc_id" ]; then
        echo -e "${ORANGE}Waiting for Vespa to be ready...${NC}"
        max_attempts=30
        attempt=1
        while ! curl --output /dev/null --silent --fail "$VESPA_URL"; do
            if [ $attempt -eq $max_attempts ]; then
                echo -e "${RED}Error: Vespa not available after $max_attempts attempts${NC}"
                exit 1
            fi
            printf '.'
            sleep 3
            attempt=$((attempt + 1))
        done
        echo -e "${GREEN}Vespa is ready${NC}"
    else
        echo -e "${ORANGE}Checking if document exists...${NC}"
        status=$(curl -s -o /dev/null -w "%{http_code}" "$VESPA_URL/document/v1/doctype/doctype/docid/$expected_doc_id")
        
        if [ "$status" = "$expected_status" ]; then
            echo -e "${GREEN}✓ Test passed${NC}"
        else
            echo -e "${RED}✗ Test failed - (HTTP $status)${NC}"
            failed_count=$((failed_count + 1))
        fi
    fi
}

check_content() {
    test_count=$((test_count + 1))

    local file=$1
    local pattern=$2
    local desc=$3
    
    if grep -q "$pattern" "$file"; then
        echo -e "${GREEN}✓ $desc - FOUND${NC}"
        return 0
    else
        echo -e "${RED}✗ $desc - NOT FOUND${NC}"
        failed_count=$((failed_count + 1))
        return 1
    fi
}

run_cloud_test() {
    log_folder="/tmp/logstash_log"

    # Run Logstash with the cloud config and capture output
    echo "testing" | $LOGSTASH_HOME/bin/logstash -f $(pwd)/config/logstash_output_test_deploy_cloud.conf -l $log_folder

    # Check for correct deployment instructions in the output
    check_content "$log_folder/logstash-plain.log" "vespa config set application test-tenant.test-application.default" "Deployment instructions"
    
    # Check if schema file was generated correctly
    check_content "/tmp/vespa_app/schemas/doctype.sd" "field test_field type string" "Schema file contains document definition"
    
    # Check for certificate generation
    check_content "$log_folder/logstash-plain.log" "Successfully generated mTLS certificates" "Certificate generation"
    
    # Check if certificates are referenced in services.xml
    check_content "/tmp/vespa_app/services.xml" "security/clients.pem" "Certificates referenced in services.xml"

    # Clean up
    rm -r $log_folder
}

### Smoke tests
echo -e "${ORANGE}Running smoke tests...${NC}"
# ID will be like test_1234567890
ID="test_$(date +%s)"
run_test "deploy_local_package" "Deploy application package locally" "$ID"
run_test "base" "Put simple document" "put,$ID" "$ID" 200
run_test "base" "Delete simple document" "remove,$ID" "$ID" 404

### DLQ tests
echo -e "${ORANGE}Running DLQ tests...${NC}"
ID="test_$(date +%s)"
#  We need the directory to exist so the DLQ input plugin doesn't scream
mkdir -p /tmp/dlq/main
run_test "dlq" "Dead letter queue test with invalid operation" "invalid_operation,$ID" "$ID" 200

### detect_schema + Vespa Cloud tests
echo -e "${ORANGE}Checking if detect_schema works with Vespa Cloud...${NC}"
run_cloud_test

### Clean up
echo -e "${ORANGE}Stopping and removing the Vespa container...${NC}"
podman stop vespa-logstash-test
podman rm -v vespa-logstash-test

# delete the package directory
rm -r /tmp/vespa_app

# remove the DLQ directory
rm -rf /tmp/dlq/main

### Print summary
echo -e "${ORANGE}Tests completed: $test_count, Failed: $failed_count${NC}"
exit $failed_count 