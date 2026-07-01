# Override of Velox's grpc.cmake to fix the PATCH_COMMAND.
# The original uses "git apply" which fails silently on tarball downloads
# (FetchContent with URL, not GIT_REPOSITORY). We use "patch -p1 -i" instead.
include_guard(GLOBAL)

velox_set_source(absl)
velox_resolve_dependency(absl CONFIG REQUIRED)

set(VELOX_GRPC_BUILD_VERSION 1.48.1)
set(VELOX_GRPC_BUILD_SHA256_CHECKSUM
    320366665d19027cda87b2368c03939006a37e0388bfd1091c8d2a96fbc93bd8)
string(
  CONCAT VELOX_GRPC_SOURCE_URL
         "https://github.com/grpc/grpc/archive/refs/tags/"
         "v${VELOX_GRPC_BUILD_VERSION}.tar.gz")

velox_resolve_dependency_url(GRPC)

message(STATUS "Building gRPC from source")

FetchContent_Declare(
  gRPC
  URL ${VELOX_GRPC_SOURCE_URL}
  URL_HASH ${VELOX_GRPC_BUILD_SHA256_CHECKSUM}
  PATCH_COMMAND
    patch -p1 -i ${CMAKE_CURRENT_LIST_DIR}/grpc/grpc-tools-target.patch
  OVERRIDE_FIND_PACKAGE EXCLUDE_FROM_ALL)

set(gRPC_ABSL_PROVIDER
    "package"
    CACHE STRING "Provider of absl library")
set(gRPC_ZLIB_PROVIDER
    "package"
    CACHE STRING "Provider of zlib library")
set(gRPC_CARES_PROVIDER
    "package"
    CACHE STRING "Provider of c-ares library")
set(gRPC_RE2_PROVIDER
    "package"
    CACHE STRING "Provider of re2 library")
set(gRPC_SSL_PROVIDER
    "package"
    CACHE STRING "Provider of ssl library")
set(gRPC_PROTOBUF_PROVIDER
    "package"
    CACHE STRING "Provider of protobuf library")
set(gRPC_INSTALL
    ON
    CACHE BOOL "Generate installation target")
FetchContent_MakeAvailable(gRPC)
add_library(gRPC::grpc ALIAS grpc)
add_library(gRPC::grpc++ ALIAS grpc++)
add_executable(gRPC::grpc_cpp_plugin ALIAS grpc_cpp_plugin)
