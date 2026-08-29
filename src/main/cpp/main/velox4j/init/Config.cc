/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "Config.h"

#include <atomic>

namespace velox4j {
using namespace facebook::velox;

namespace {
std::string presetToStr(Preset p) {
  switch (p) {
    case Preset::SPARK:
      return "SPARK";
    case Preset::FLINK:
      return "FLINK";
  }
  VELOX_FAIL("Unknown Preset value: {}", static_cast<int>(p));
}

Preset presetFromString(const std::string& key, const std::string& value) {
  if (value == "SPARK") {
    return Preset::SPARK;
  }
  if (value == "FLINK") {
    return Preset::FLINK;
  }
  VELOX_FAIL(
      "Invalid configuration for key '{}'. Value '{}' cannot be converted to type velox4j::Preset (expected SPARK or FLINK).",
      key,
      value);
}
} // namespace

config::ConfigBase::Entry<Preset> VELOX4J_INIT_PRESET(
    "velox4j.init.preset",
    Preset::SPARK,
    presetToStr,
    presetFromString);
config::ConfigBase::Entry<bool> VELOX4J_MEMORY_MANAGER_FAIL_ON_LEAK(
    "velox4j.memory-manager.fail-on-leak",
    true);

namespace {
std::atomic<bool> memoryManagerFailOnLeak_{false};
}

void setMemoryManagerFailOnLeak(bool failOnLeak) {
  memoryManagerFailOnLeak_.store(failOnLeak);
}

bool memoryManagerFailOnLeak() {
  return memoryManagerFailOnLeak_.load();
}
} // namespace velox4j
