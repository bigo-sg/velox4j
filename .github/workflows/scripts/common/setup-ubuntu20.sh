#!/bin/bash
export DEBIAN_FRONTEND=noninteractive

set -e
set -o pipefail
set -u

# APT update.
apt-get update

# Install essentials.
apt-get install -y sudo locales wget tar tzdata git ccache ninja-build build-essential
apt-get install -y llvm-14-dev clang-14 libiberty-dev libdwarf-dev libre2-dev libz-dev
apt-get install -y liblzo2-dev libzstd-dev libsnappy-dev libdouble-conversion-dev libssl-dev
apt-get install -y libboost-all-dev libcurl4-openssl-dev curl zip unzip tar pkg-config
apt-get install -y autoconf-archive bison flex libfl-dev libc-ares-dev libicu-dev
apt-get install -y libgoogle-glog-dev libbz2-dev libgflags-dev libgmock-dev libevent-dev
apt-get install -y liblz4-dev librdkafka-dev libsodium-dev libelf-dev
apt-get install -y autoconf automake g++ libnuma-dev libtool numactl unzip libndctl-dev
apt-get install -y openjdk-11-jdk maven chrpath patchelf

# Install CMake >= 3.28 via pip.
apt-get install -y python3 python3-pip
pip3 install cmake==3.28.3

# Install GCC 11.
apt-get install -y software-properties-common
add-apt-repository ppa:ubuntu-toolchain-r/test
apt-get update
apt-get install -y gcc-11 g++-11
rm -f /usr/bin/gcc /usr/bin/g++
ln -s /usr/bin/gcc-11 /usr/bin/gcc
ln -s /usr/bin/g++-11 /usr/bin/g++
cc --version
c++ --version
