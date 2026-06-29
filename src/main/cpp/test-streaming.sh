#!/bin/bash

set -e
set -o pipefail
set -u

BASE_DIR=$(dirname "$0")
NUM_THREADS=$(nproc)
SOURCE_DIR=$BASE_DIR
BUILD_DIR=$BASE_DIR/build
BUILD_TEST_DIR=$BUILD_DIR/test

# Build only the stateful streaming test target.
cmake -DCMAKE_BUILD_TYPE=Release -DVELOX4J_ENABLE_CCACHE=ON -DVELOX4J_BUILD_TESTING=ON -S "$SOURCE_DIR" -B "$BUILD_DIR"
cmake --build "$BUILD_DIR" --target velox4j_stateful_streaming_test -j "$NUM_THREADS"

# Run the stateful streaming test.
"$BUILD_TEST_DIR/velox4j_stateful_streaming_test"
