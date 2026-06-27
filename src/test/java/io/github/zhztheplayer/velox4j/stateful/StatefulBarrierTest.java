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
package io.github.zhztheplayer.velox4j.stateful;

import org.junit.Assert;
import org.junit.Test;

public class StatefulBarrierTest {

  @Test
  public void testBarrierProperties() {
    StatefulBarrier barrier = new StatefulBarrier("node-1", 42L);
    Assert.assertEquals("node-1", barrier.getNodeId());
    Assert.assertEquals(42L, barrier.getCheckpointId());
    Assert.assertTrue(barrier.isBarrier());
    Assert.assertFalse(barrier.isWatermark());
    Assert.assertFalse(barrier.isRecord());
  }

  @Test
  public void testAsBarrier() {
    StatefulElement element = new StatefulBarrier("node-2", 99L);
    Assert.assertTrue(element.isBarrier());
    StatefulBarrier barrier = element.asBarrier();
    Assert.assertEquals(99L, barrier.getCheckpointId());
  }

  @Test
  public void testRecordIsNotBarrier() {
    StatefulElement record = new StatefulRecord("node-1", 0L, -1L, false, -1);
    Assert.assertFalse(record.isBarrier());
    Assert.assertTrue(record.isRecord());
  }

  @Test
  public void testWatermarkIsNotBarrier() {
    StatefulElement watermark = new StatefulWatermark("node-1", 1000L);
    Assert.assertFalse(watermark.isBarrier());
    Assert.assertTrue(watermark.isWatermark());
  }
}
