#!/bin/bash
export DEBIAN_FRONTEND=noninteractive

set -e
set -o pipefail
set -u

# APT update.
apt-get update

# Install basics.
apt-get install -y sudo locales wget tar tzdata git ccache ninja-build build-essential
apt-get install -y curl zip unzip tar pkg-config gnupg lsb-release software-properties-common
apt-get install -y chrpath patchelf openjdk-11-jdk maven python3 python3-pip

# Install CMake >= 3.28 via pip.
pip3 install cmake==3.28.3

# Add LLVM apt source (Ubuntu 20.04 default repos don't have LLVM 14).
wget -qO- https://apt.llvm.org/llvm-snapshot.gpg.key | apt-key add -
add-apt-repository "deb http://apt.llvm.org/focal/ llvm-toolchain-focal-14 main"
apt-get update
apt-get install -y llvm-14-dev clang-14

# Install GCC 11 from Ubuntu Toolchain PPA (import key manually to avoid keyserver timeout).
wget -qO- "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x1E9377A2BA9EF27F&options=mr" | apt-key add -
echo "deb http://ppa.launchpad.net/ubuntu-toolchain-r/test/ubuntu focal main" > /etc/apt/sources.list.d/ubuntu-toolchain-r-ppa.list
apt-get update
apt-get install -y gcc-11 g++-11
rm -f /usr/bin/gcc /usr/bin/g++
ln -s /usr/bin/gcc-11 /usr/bin/gcc
ln -s /usr/bin/g++-11 /usr/bin/g++

# Download Velox source and run its official setup script to install all
# C++ dependencies (Boost 1.84.0, Arrow, folly, fmt, thrift, protobuf, etc.)
# from source into /usr/local.
VELOX_REF=9d779d87829161fa336a1342356d209853d07112
cd /tmp
git clone https://github.com/bigo-sg/velox.git velox-setup
cd velox-setup
git checkout ${VELOX_REF}
export CC=/usr/bin/gcc-11
export CXX=/usr/bin/g++-11
PROMPT_ALWAYS_RESPOND=n INSTALL_PREREQUISITES=N bash scripts/setup-ubuntu.sh

# ---------------------------------------------------------------
# Install absl, gRPC, and RocksDB as SYSTEM libraries so that
# Velox's AUTO dependency resolution picks them up via find_package
# instead of building BUNDLED (which causes a "tools" target conflict
# between RocksDB and gRPC FetchContent builds).
# ---------------------------------------------------------------
INSTALL_PREFIX=${INSTALL_PREFIX:-/usr/local}
NPROC=$(getconf _NPROCESSORS_ONLN)
BUILD_DIR=/tmp/velox-deps
mkdir -p ${BUILD_DIR}

# --- absl 20240116.2 ---
cd ${BUILD_DIR}
wget -q https://github.com/abseil/abseil-cpp/archive/refs/tags/20240116.2.tar.gz -O absl.tar.gz
tar xzf absl.tar.gz
cd abseil-cpp-20240116.2
cmake -B build -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=${INSTALL_PREFIX} \
  -DABSL_BUILD_TESTING=OFF -DABSL_PROPAGATE_CXX_STD=ON -DABSL_ENABLE_INSTALL=ON
cmake --build build -j ${NPROC}
cmake --install build

# --- gRPC 1.48.1 ---
cd ${BUILD_DIR}
wget -q https://github.com/grpc/grpc/archive/refs/tags/v1.48.1.tar.gz -O grpc.tar.gz
tar xzf grpc.tar.gz
cd grpc-1.48.1
# gRPC needs c-ares, re2, zlib, protobuf, openssl, absl — all already installed.
cmake -B build -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=${INSTALL_PREFIX} \
  -DgRPC_ABSL_PROVIDER=package \
  -DgRPC_ZLIB_PROVIDER=package \
  -DgRPC_CARES_PROVIDER=package \
  -DgRPC_RE2_PROVIDER=package \
  -DgRPC_SSL_PROVIDER=package \
  -DgRPC_PROTOBUF_PROVIDER=package \
  -DgRPC_BUILD_TESTS=OFF \
  -DgRPC_INSTALL=ON
cmake --build build -j ${NPROC}
cmake --install build

# --- RocksDB (FRocksDB-6.20.3) ---
cd ${BUILD_DIR}
wget -q https://github.com/ververica/frocksdb/archive/refs/heads/FRocksDB-6.20.3.zip -O frocksdb.zip
unzip -q frocksdb.zip
cd frocksdb-FRocksDB-6.20.3
cmake -B build -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=${INSTALL_PREFIX} \
  -DROCKSDB_BUILD_SHARED=ON \
  -DWITH_TESTS=OFF \
  -DWITH_BENCHMARK_TOOLS=OFF \
  -DWITH_TOOLS=OFF \
  -DWITH_GFLAGS=OFF
cmake --build build -j ${NPROC}
cmake --install build
ldconfig

# Clean up all build artifacts and downloaded sources.
cd /
rm -rf ${BUILD_DIR} /tmp/velox-setup
