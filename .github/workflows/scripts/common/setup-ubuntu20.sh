#!/bin/bash
export DEBIAN_FRONTEND=noninteractive

set -e
set -o pipefail
set -u

# APT update.
apt-get update

# Install basics needed for adding repos.
apt-get install -y sudo locales wget tar tzdata git ccache ninja-build build-essential
apt-get install -y curl zip unzip tar pkg-config gnupg lsb-release software-properties-common

# Add LLVM apt source (Ubuntu 20.04 default repos don't have LLVM 14).
wget -qO- https://apt.llvm.org/llvm-snapshot.gpg.key | apt-key add -
add-apt-repository "deb http://apt.llvm.org/focal/ llvm-toolchain-focal-14 main"
apt-get update
apt-get install -y llvm-14-dev clang-14

# Install remaining build dependencies.
apt-get install -y libiberty-dev libdwarf-dev libre2-dev libz-dev
apt-get install -y liblzo2-dev libzstd-dev libsnappy-dev libdouble-conversion-dev libssl-dev
apt-get install -y libboost-all-dev libcurl4-openssl-dev
apt-get install -y autoconf-archive bison flex libfl-dev libc-ares-dev libicu-dev
apt-get install -y libgoogle-glog-dev libbz2-dev libgflags-dev libgmock-dev libevent-dev
apt-get install -y liblz4-dev librdkafka-dev libsodium-dev libelf-dev
apt-get install -y autoconf automake g++ libnuma-dev libtool numactl unzip libndctl-dev
apt-get install -y openjdk-11-jdk maven chrpath patchelf

# Install CMake >= 3.28 via pip.
apt-get install -y python3 python3-pip
pip3 install cmake==3.28.3

# Install GCC 11.
add-apt-repository ppa:ubuntu-toolchain-r/test
apt-get update
apt-get install -y gcc-11 g++-11
rm -f /usr/bin/gcc /usr/bin/g++
ln -s /usr/bin/gcc-11 /usr/bin/gcc
ln -s /usr/bin/g++-11 /usr/bin/g++
cc --version
c++ --version
