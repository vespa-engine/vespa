#!/bin/bash

# Configuration
LOGSTASH_HOME="/opt/logstash/logstash-current"
VESPA_URL="http://localhost:8080"
VESPA_CLUSTER="used_car"
PLUGIN_VERSION=$(cat ../VERSION)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
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
echo "Building plugin..."
cd ..
./gradlew clean gem
# bail if the plugin is not built
if [ $? -ne 0 ]; then
    echo -e "${RED}Error: Plugin not built${NC}"
    exit 1
fi

cd integration-test
echo "Installing plugin..."
$LOGSTASH_HOME/bin/logstash-plugin install --no-verify ../logstash-output-vespa_feed-$PLUGIN_VERSION.gem
# bail if the plugin is not installed
if [ $? -ne 0 ]; then
    echo -e "${RED}Error: Plugin not installed${NC}"
    exit 1
fi

# Wait for Vespa to be ready
echo "Checking Vespa availability..."
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
echo "Running tests..."
test_count=0
failed_count=0

# fill in the Vespa cluster
sed "s/VESPA_CLUSTER/$VESPA_CLUSTER/" config/logstash_output_test.conf > config/logstash_output_test_configured.conf

run_test() {
    local test_name=$1
    local input=$2
    local expected_doc_id=$3
    local expected_status=$4
    


    test_count=$((test_count + 1))
    
    echo "Test: $test_name"
    echo "$input" | $LOGSTASH_HOME/bin/logstash -f $(pwd)/config/logstash_output_test_configured.conf
    
    # Wait for document to be available
    sleep 2
    
    # Check if document exists
    status=$(curl -s -o /dev/null -w "%{http_code}" "$VESPA_URL/document/v1/cars/$VESPA_CLUSTER/docid/$expected_doc_id")
    
    if [ "$status" = "$expected_status" ]; then
        echo -e "${GREEN}✓ Test passed${NC}"
    else
        echo -e "${RED}✗ Test failed - (HTTP $status)${NC}"
        failed_count=$((failed_count + 1))
    fi
}

# ID will be like test_1234567890
ID="test_$(date +%s)"

# Run tests
run_test "Put simple document" "put,$ID" "$ID" 200
run_test "Delete simple document" "
remove,$ID" "$ID" 404


# Print summary
echo "Tests completed: $test_count, Failed: $failed_count"
exit $failed_count 