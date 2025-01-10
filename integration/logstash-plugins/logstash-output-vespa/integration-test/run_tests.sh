#!/bin/bash

# Configuration
LOGSTASH_HOME="/opt/logstash/logstash-current"
VESPA_URL="http://localhost:8080"
DOCUMENT_TYPE="used_car"
PLUGIN_VERSION=$(cat ../VERSION)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
ORANGE='\033[0;33m'
NC='\033[0m'

command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Check prerequisites
if [ ! -d "$LOGSTASH_HOME" ]; then
    echo -e "${RED}Error: Logstash not found at $LOGSTASH_HOME${NC}"
    exit 1
fi

if ! command_exists curl; then
    echo -e "${RED}Error: curl is required but not installed${NC}"
    exit 1
fi

# Build and install plugin
echo -e "${ORANGE}Building plugin...${NC}"
cd ..
./gradlew clean gem
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

# Wait for Vespa to be ready
echo -e "${ORANGE}Checking Vespa availability...${NC}"
max_attempts=30
attempt=1
while ! curl --output /dev/null --silent --fail "$VESPA_URL"; do
    if [ $attempt -eq $max_attempts ]; then
        echo -e "${RED}Error: Vespa not available after $max_attempts attempts${NC}"
        exit 1
    fi
    printf '.'
    sleep 1
    attempt=$((attempt + 1))
done
echo -e "${GREEN}Vespa is ready${NC}"

# Run test cases
echo -e "${ORANGE}Running tests...${NC}"
test_count=0
failed_count=0


run_test() {
    local test_name=$1
    local test_description=$2
    local input=$3
    local expected_doc_id=$4
    local expected_status=$5

    test_count=$((test_count + 1))

    # fill in the document type
    sed "s/DOCUMENT_TYPE/$DOCUMENT_TYPE/" config/logstash_output_test_${test_name}.conf > config/logstash_output_test_${test_name}_configured.conf
    
    echo -e "${ORANGE}Test: $test_description${NC}"
    echo "$input" | $LOGSTASH_HOME/bin/logstash -f $(pwd)/config/logstash_output_test_${test_name}_configured.conf &
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
        echo -e "${ORANGE}Giving Logstash two seconds to process...${NC}"
        sleep 2
        echo -e "${ORANGE}Killing Logstash...${NC}"
        kill $logstash_pid
        echo -e "${ORANGE}Waiting for Logstash to exit...${NC}"
        wait $logstash_pid
    fi

    echo -e "${ORANGE}Checking if document exists...${NC}"
    status=$(curl -s -o /dev/null -w "%{http_code}" "$VESPA_URL/document/v1/cars/$DOCUMENT_TYPE/docid/$expected_doc_id")
    
    if [ "$status" = "$expected_status" ]; then
        echo -e "${GREEN}✓ Test passed${NC}"
    else
        echo -e "${RED}✗ Test failed - (HTTP $status)${NC}"
        failed_count=$((failed_count + 1))
    fi
}


### Smoke tests
# ID will be like test_1234567890
ID="test_$(date +%s)"
run_test "base" "Put simple document" "put,$ID" "$ID" 200
run_test "base" "Delete simple document" "remove,$ID" "$ID" 404

### DLQ tests
ID="test_$(date +%s)"
#  We need the directory to exist so the DLQ input plugin doesn't scream
mkdir -p /tmp/dlq/main
run_test "dlq" "Dead letter queue test with invalid operation" "invalid_operation,$ID" "$ID" 200
# clean up
rm -rf /tmp/dlq/main
run_test "base" "Delete DLQ document again" "remove,$ID" "$ID" 404


# Print summary
echo -e "${ORANGE}Tests completed: $test_count, Failed: $failed_count${NC}"
exit $failed_count 