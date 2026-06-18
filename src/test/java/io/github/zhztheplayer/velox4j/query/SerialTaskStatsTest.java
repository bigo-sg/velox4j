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
package io.github.zhztheplayer.velox4j.query;

import org.junit.Assert;
import org.junit.Test;

public class SerialTaskStatsTest {

  @Test
  public void testPlanStatsReturnsAllPlanStats() {
    final SerialTaskStats stats =
        SerialTaskStats.fromJson(
            "{"
                + "\"planStats\":["
                + "{\"planNodeId\":\"scan-1\",\"operatorType\":\"TableScan\"},"
                + "{\"planNodeId\":\"project-1\",\"operatorType\":\"FilterProject\"}"
                + "]"
                + "}");

    Assert.assertEquals(2, stats.planStats().size());
    Assert.assertEquals("scan-1", stats.planStats().get(0).get("planNodeId").asText());
    Assert.assertEquals("project-1", stats.planStats().get(1).get("planNodeId").asText());
  }
}
