#!/bin/bash

# Configuration
LOGSTASH_HOME="/opt/logstash/logstash-current"
VESPA_URL="http://localhost:8080"
VESPA_CLUSTER="used_car"

# extract the plugin version from the gemspec
PLUGIN_VERSION=$(grep version ../logstash-input-vespa.gemspec | awk -F"'" '{print $2}')
if [ -z "$PLUGIN_VERSION" ]; then
    echo -e "${RED}Error: Failed to extract plugin version${NC}"
    exit 1
else
    echo "Plugin version: $PLUGIN_VERSION"
fi

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
gem build logstash-input-vespa.gemspec
cd integration-test

echo "Installing plugin..."
$LOGSTASH_HOME/bin/logstash-plugin install --no-verify ../logstash-input-vespa-${PLUGIN_VERSION}.gem

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

run_test() {
    local test_name=$1
    local doc_id=$2
    local doc_content=$3
    
    test_count=$((test_count + 1))
    echo "Test: $test_name"

    # Feed document to Vespa
    curl -X POST -H "Content-Type:application/json" --data "$doc_content" \
        "$VESPA_URL/document/v1/cars/$VESPA_CLUSTER/docid/$doc_id"
    
    # Create config file with actual ID
    sed "s/TEST_DOC_ID/$doc_id/" config/logstash_input_test.conf > config/logstash_input_test_with_id.conf

    # fill in the Vespa cluster
    sed "s/VESPA_CLUSTER/$VESPA_CLUSTER/" config/logstash_input_test_with_id.conf > config/logstash_input_test_with_id2.conf
    mv config/logstash_input_test_with_id2.conf config/logstash_input_test_with_id.conf

    # Run Logstash
    $LOGSTASH_HOME/bin/logstash -f $(pwd)/config/logstash_input_test_with_id.conf
    
    # Check output file
    OUTPUT_FILE="/tmp/output.json"
    if [ -f "$OUTPUT_FILE" ] && grep -q "$doc_id" "$OUTPUT_FILE"; then
        echo -e "${GREEN}✓ Test passed${NC}"
    else
        echo -e "${RED}✗ Test failed - Document not found in output${NC}"
        failed_count=$((failed_count + 1))
    fi
    
    # Clean up
    rm -f "$OUTPUT_FILE"
}

# Create output directory
mkdir -p data

# ID will be like test_1234567890
ID="test_$(date +%s)"

# Run tests
run_test "Simple document" "$ID" '{
    "fields": {
        "id": "'$ID'"
    }
}'

# Print summary
echo "Tests completed: $test_count, Failed: $failed_count"
exit $failed_count 