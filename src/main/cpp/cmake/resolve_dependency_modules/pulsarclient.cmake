# Copyright (c) Facebook, Inc. and its affiliates.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
include_guard(GLOBAL)

set(BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_PERF_TOOLS OFF CACHE BOOL "" FORCE)
set(BUILD_STATIC_LIB OFF CACHE BOOL "" FORCE)
set(BUILD_DYNAMIC_LIB ON CACHE BOOL "" FORCE)

FetchContent_Declare(
  pulsarclient
  GIT_REPOSITORY https://github.com/apache/pulsar-client-cpp.git
  GIT_TAG v3.3.0)

FetchContent_MakeAvailable(pulsarclient)

if(TARGET PULSAR_OBJECT_LIB)
  target_compile_options(PULSAR_OBJECT_LIB PRIVATE -Wno-error)
endif()

if(TARGET pulsarShared)
  set(PulsarClient_INCLUDE_DIR ${pulsarclient_SOURCE_DIR}/include)
  target_include_directories(
    pulsarShared
    PUBLIC ${pulsarclient_SOURCE_DIR} ${pulsarclient_SOURCE_DIR}/include
           ${pulsarclient_BINARY_DIR}/include)

  if(NOT TARGET PulsarClient::pulsar)
    add_library(PulsarClient::pulsar ALIAS pulsarShared)
  endif()
endif()
