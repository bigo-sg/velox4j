#!/bin/bash
# Setup script for velox4j CI Docker image (Ubuntu 20.04).
#
# Installs ALL C++ dependencies as SYSTEM libraries in /usr/local so that
# Velox's AUTO dependency resolution finds them via find_package() and
# skips the BUNDLED FetchContent builds entirely. This:
#   1. Avoids the RocksDB/gRPC "tools" target conflict.
#   2. Eliminates redundant dep compilation on every CI run.
#   3. Makes CI faster (only Velox + velox4j are compiled).
#
# When a dependency is upgraded in Velox, rebuild this Docker image.
export DEBIAN_FRONTEND=noninteractive

set -e
set -o pipefail
set -u

INSTALL_PREFIX=${INSTALL_PREFIX:-/usr/local}
NPROC=$(getconf _NPROCESSORS_ONLN)
BUILD_DIR=/tmp/velox-deps

# ---------------------------------------------------------------------------
# 1. APT packages (basics + system libs with pkg-config but no cmake config)
# ---------------------------------------------------------------------------
apt-get update

apt-get install -y sudo locales wget tar tzdata git ccache ninja-build build-essential
apt-get install -y curl zip unzip tar pkg-config gnupg lsb-release software-properties-common
apt-get install -y chrpath patchelf openjdk-11-jdk maven python3 python3-pip
# These apt packages are fine as-is (Velox uses find_package with fallback):
apt-get install -y libssl-dev libcurl4-openssl-dev libicu-dev libbz2-dev
apt-get install -y liblz4-dev libzstd-dev libsnappy-dev libsodium-dev liblzo2-dev
apt-get install -y libgoogle-glog-dev libgflags-dev libgmock-dev libevent-dev
apt-get install -y libelf-dev libdwarf-dev bison flex libfl-dev
apt-get install -y libunwind-dev

# CMake >= 3.28 via pip.
pip3 install cmake==3.28.3

# LLVM 14 (for clang-format, used by Velox's format checks).
wget -qO- https://apt.llvm.org/llvm-snapshot.gpg.key | apt-key add -
add-apt-repository "deb http://apt.llvm.org/focal/ llvm-toolchain-focal-14 main"
apt-get update
apt-get install -y llvm-14-dev clang-14

# GCC 11 (required by Velox on Ubuntu 20.04).
wget -qO- "https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x1E9377A2BA9EF27F&options=mr" | apt-key add -
echo "deb http://ppa.launchpad.net/ubuntu-toolchain-r/test/ubuntu focal main" > /etc/apt/sources.list.d/ubuntu-toolchain-r-ppa.list
apt-get update
apt-get install -y gcc-11 g++-11
rm -f /usr/bin/gcc /usr/bin/g++
ln -s /usr/bin/gcc-11 /usr/bin/gcc
ln -s /usr/bin/g++-11 /usr/bin/g++

export CC=/usr/bin/gcc-11
export CXX=/usr/bin/g++-11

# ---------------------------------------------------------------------------
# 2. Velox's official setup script
#    Installs into /usr/local: fmt, folly, boost, protobuf, thrift, arrow,
#    geos, stemmer, duckdb, librdkafka, cppkafka, fizz, wangle, mvfst, fbthrift.
#    Also installs double-conversion via apt (with cmake config).
# ---------------------------------------------------------------------------
VELOX_REF=9d779d87829161fa336a1342356d209853d07112
cd /tmp
git clone https://github.com/bigo-sg/velox.git velox-setup
cd velox-setup
git checkout ${VELOX_REF}
PROMPT_ALWAYS_RESPOND=n INSTALL_PREREQUISITES=N bash scripts/setup-ubuntu.sh
cd /
rm -rf /tmp/velox-setup

# ---------------------------------------------------------------------------
# 3. Remaining deps that Velox's setup-ubuntu.sh does NOT install, or whose
#    apt versions are too old / lack CMake config files.
#    Install order matters: c-ares -> absl -> re2 -> gRPC -> RocksDB.
#    xsimd and simdjson have no inter-dependencies.
# ---------------------------------------------------------------------------
mkdir -p ${BUILD_DIR}

# --- c-ares (cares-1_13_0, Velox's pinned version) ---
# Ubuntu 20.04's libc-ares-dev (1.15.0) lacks c-ares-config.cmake.
cd ${BUILD_DIR}
wget -q https://github.com/c-ares/c-ares/archive/refs/tags/cares-1_13_0.tar.gz -O cares.tar.gz
tar xzf cares.tar.gz
cd c-ares-cares-1_13_0
cmake -B build -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=${INSTALL_PREFIX} \
  -DCARES_STATIC=ON -DCARES_SHARED=OFF -DCARES_INSTALL=ON
cmake --build build -j ${NPROC}
cmake --install build

# --- double-conversion (3.1.5) ---
# apt package exists but ensure cmake config is available.
if ! cmake --find-package -DNAME=double-conversion -DCOMPILER_ID=GNU -DLANGUAGE=CXX -DMODE=EXIST 2>/dev/null; then
  cd ${BUILD_DIR}
  wget -q https://github.com/google/double-conversion/archive/refs/tags/v3.1.5.tar.gz -O dc.tar.gz
  tar xzf dc.tar.gz
  cd double-conversion-3.1.5
  cmake -B build -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=${INSTALL_PREFIX} \
    -DBUILD_SHARED_LIBS=ON
  cmake --build build -j ${NPROC}
  cmake --install build
fi

# --- xsimd 10.0.0 ---
cd ${BUILD_DIR}
wget -q https://github.com/xtensor-stack/xsimd/archive/refs/tags/10.0.0.tar.gz -O xsimd.tar.gz
tar xzf xsimd.tar.gz
cd xsimd-10.0.0
cmake -B build -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=${INSTALL_PREFIX}
cmake --build build -j ${NPROC}
cmake --install build

# --- simdjson 3.9.3 ---
cd ${BUILD_DIR}
wget -q https://github.com/simdjson/simdjson/archive/refs/tags/v3.9.3.tar.gz -O simdjson.tar.gz
tar xzf simdjson.tar.gz
cd simdjson-3.9.3
cmake -B build -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=${INSTALL_PREFIX} \
  -DSIMDJSON_BUILD_STATIC=ON -DSIMDJSON_BUILD_TESTS=OFF
cmake --build build -j ${NPROC}
cmake --install build

# --- absl 20240116.2 ---
cd ${BUILD_DIR}
wget -q https://github.com/abseil/abseil-cpp/archive/refs/tags/20240116.2.tar.gz -O absl.tar.gz
tar xzf absl.tar.gz
cd abseil-cpp-20240116.2
cmake -B build -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=${INSTALL_PREFIX} \
  -DABSL_BUILD_TESTING=OFF -DABSL_PROPAGATE_CXX_STD=ON -DABSL_ENABLE_INSTALL=ON
cmake --build build -j ${NPROC}
cmake --install build

# --- re2 2024-07-02 ---
# Ubuntu 20.04's libre2-dev (20200101) is too old and lacks re2Config.cmake.
cd ${BUILD_DIR}
wget -q https://github.com/google/re2/archive/refs/tags/2024-07-02.tar.gz -O re2.tar.gz
tar xzf re2.tar.gz
cd re2-2024-07-02
cmake -B build -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_PREFIX=${INSTALL_PREFIX} \
  -DRE2_BUILD_TESTING=OFF -DRE2_USE_ICU=ON -DBUILD_SHARED_LIBS=ON \
  -DCMAKE_PREFIX_PATH=${INSTALL_PREFIX}
cmake --build build -j ${NPROC}
cmake --install build

# --- gRPC 1.48.1 ---
cd ${BUILD_DIR}
wget -q https://github.com/grpc/grpc/archive/refs/tags/v1.48.1.tar.gz -O grpc.tar.gz
tar xzf grpc.tar.gz
cd grpc-1.48.1
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

# ---------------------------------------------------------------------------
# 4. Clean up all build artifacts and downloaded sources.
# ---------------------------------------------------------------------------
cd /
rm -rf ${BUILD_DIR}
rm -rf /var/lib/apt/lists/* /root/.cache/pip
apt-get clean
