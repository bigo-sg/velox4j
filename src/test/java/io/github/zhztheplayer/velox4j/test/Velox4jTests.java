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
package io.github.zhztheplayer.velox4j.test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.github.zhztheplayer.velox4j.Velox4j;
import io.github.zhztheplayer.velox4j.config.Preset;

public class Velox4jTests {
  private static final AtomicBoolean initialized = new AtomicBoolean(false);
  private static final AtomicReference<String> initializedPreset = new AtomicReference<>(null);

  public static void ensureInitialized() {
    initializeWith("SPARK");
  }

  public static void ensureInitializedForFlink() {
    initializeWith("FLINK");
  }

  private static void initializeWith(String preset) {
    if (!initialized.compareAndSet(false, true)) {
      String existing = initializedPreset.get();
      if (!preset.equals(existing)) {
        throw new IllegalStateException(
            "Velox4j already initialized under preset "
                + existing
                + ", cannot switch to "
                + preset
                + " in the same JVM. Keep FLINK-preset tests in a separate surefire"
                + " execution so they run in an isolated fork.");
      }
      return;
    }
    initializedPreset.set(preset);
    if (!preset.equals("SPARK")) {
      Velox4j.configure(Preset.KEY, preset);
    }
    Velox4j.initialize();
  }
}
