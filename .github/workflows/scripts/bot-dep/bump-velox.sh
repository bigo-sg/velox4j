#!/bin/bash

set -e
set -o pipefail
set -u

BASE_DIR=$(dirname "$0")
VELOX_UPSTREAM_REPO=bigo-sg/velox
VELOX_UPSTREAM_BRANCH=gluten-20260829
VELOX_CPP_ROOT="$BASE_DIR/../../../../src/main/cpp"
VELOX_REF_FILE="$VELOX_CPP_ROOT/velox-ref.txt"
VELOX_REF_HASH_FILE="$VELOX_CPP_ROOT/velox-ref-hash.txt"

LATEST_COMMIT_HASH="$(git ls-remote "https://github.com/$VELOX_UPSTREAM_REPO.git" "refs/heads/$VELOX_UPSTREAM_BRANCH" | awk '{print $1;}' | head -n 1)"

echo "The latest commit for $VELOX_UPSTREAM_REPO is $LATEST_COMMIT_HASH"

VELOX_UPSTREAM_SOURCE_URL="https://github.com/${VELOX_UPSTREAM_REPO}/archive/${LATEST_COMMIT_HASH}.zip"
VELOX_SRC_FILENAME="$BASE_DIR/velox-src-$LATEST_COMMIT_HASH.zip"
wget "$VELOX_UPSTREAM_SOURCE_URL" -O "$VELOX_SRC_FILENAME"
VELOX_SRC_MD5="$(md5sum "$VELOX_SRC_FILENAME" | awk '{print $1}')"

echo "The MD5 HASH for $VELOX_SRC_FILENAME is $VELOX_SRC_MD5"

echo "$LATEST_COMMIT_HASH" > "$VELOX_REF_FILE"
echo "$VELOX_SRC_MD5" > "$VELOX_REF_HASH_FILE"

rm "$VELOX_SRC_FILENAME"

echo "Successfully updated velox-ref.txt and velox-ref-hash.txt"
