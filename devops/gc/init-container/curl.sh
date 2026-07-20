#!/bin/sh

MAX_RETRIES=3
RETRY_INTERVAL=30
ENDPOINT=http://partition/api/partition/v1/partitions

# Curl function
check_endpoint() {
    response=$(curl -sSf "$ENDPOINT")
    if [ -z "$(echo "$response" | tr -d '[:space:]' | tr -d '[' | tr -d ']')" ]; then
        echo "$response"
        return 1
    else
        echo "$response"
        return 0
    fi
}

# For loop
for i in $(seq 1 $MAX_RETRIES); do
    echo "Attempt $i..."
    if check_endpoint; then
        echo "Endpoint is available."
        exit 0
    else
        echo "Endpoint is not available. Retrying in $RETRY_INTERVAL seconds..."
        sleep $RETRY_INTERVAL
    fi
done

echo "Maximum retries exceeded. Endpoint is not available."
exit 1
