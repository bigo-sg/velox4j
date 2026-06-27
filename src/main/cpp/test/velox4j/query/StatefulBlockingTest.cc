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

#include <gtest/gtest.h>
#include <velox/exec/tests/utils/PlanBuilder.h>
#include <velox/experimental/stateful/StatefulOperator.h>
#include <velox/experimental/stateful/StatefulTask.h>
#include <velox/vector/tests/utils/VectorTestBase.h>
#include <unordered_set>
#include "velox4j/memory/AllocationListener.h"
#include "velox4j/memory/MemoryManager.h"
#include "velox4j/test/Init.h"

namespace velox4j {
namespace {

using namespace facebook::velox;
using namespace facebook::velox::exec;
using namespace facebook::velox::exec::test;

class BlockingSourceOperator : public SourceOperator {
 public:
  BlockingSourceOperator(DriverCtx* driverCtx, RowTypePtr outputType)
      : SourceOperator(
            driverCtx,
            std::move(outputType),
            0,
            "blocked-source",
            "BlockingSource") {
    auto [promise, future] =
        makeVeloxContinuePromiseContract("BlockingSourceOperator");
    promise_ = std::move(promise);
    future_ = std::move(future);
  }

  RowVectorPtr getOutput() override {
    ++getOutputCalls_;
    VELOX_CHECK(!blocked_, "getOutput called while operator is blocked");
    return nullptr;
  }

  BlockingReason isBlocked(ContinueFuture* future) override {
    if (!blocked_) {
      return BlockingReason::kNotBlocked;
    }
    *future = std::move(future_);
    return BlockingReason::kWaitForProducer;
  }

  bool isFinished() override {
    return !blocked_;
  }

  void unblock() {
    blocked_ = false;
    std::move(promise_).setValue();
  }

  int getOutputCalls() const {
    return getOutputCalls_;
  }

 private:
  bool blocked_{true};
  ContinuePromise promise_{ContinuePromise::makeEmpty()};
  ContinueFuture future_{ContinueFuture::makeEmpty()};
  int getOutputCalls_{0};
};

class StatefulBlockingTest : public testing::Test,
                             public facebook::velox::test::VectorTestBase {
 protected:
  static void SetUpTestCase() {
    testingEnsureInitializedForSpark();
  }

  StatefulBlockingTest()
      : memoryManager_(
            std::make_shared<MemoryManager>(AllocationListener::noop())) {}

  std::shared_ptr<stateful::StatefulTask> makeTask() {
    auto data = makeRowVector({makeFlatVector<int32_t>({1})});
    auto plan = PlanBuilder().values({data}).planNode();
    std::unordered_set<std::string> referencedFiles;
    core::PlanFragment planFragment{
        plan, core::ExecutionStrategy::kUngrouped, 1, referencedFiles};
    auto queryCtx = core::QueryCtx::create(
        nullptr,
        core::QueryConfig{{}},
        {},
        cache::AsyncDataCache::getInstance(),
        memoryManager_
            ->getVeloxPool(
                "StatefulBlockingTest", memory::MemoryPool::Kind::kAggregate)
            ->shared_from_this(),
        nullptr,
        "StatefulBlockingTest");
    return stateful::StatefulTask::create(
        "StatefulBlockingTest", std::move(planFragment), std::move(queryCtx));
  }

  std::shared_ptr<MemoryManager> memoryManager_;
};

TEST_F(StatefulBlockingTest, blockedSourceDoesNotCallGetOutput) {
  auto task = makeTask();
  auto driverCtx =
      std::make_unique<DriverCtx>(task, 0, 0, kUngroupedGroupId, 0);
  auto driver = Driver::testingCreate(std::move(driverCtx));
  auto source = std::make_unique<BlockingSourceOperator>(
      driver->driverCtx(), ROW({"c0"}, {INTEGER()}));
  auto* sourcePtr = source.get();
  stateful::StatefulOperator statefulOperator(std::move(source), {});

  ContinueFuture future = ContinueFuture::makeEmpty();
  statefulOperator.advanceWithFuture(&future);

  ASSERT_TRUE(future.valid());
  ASSERT_EQ(sourcePtr->getOutputCalls(), 0);

  sourcePtr->unblock();
  std::move(future).wait();
  future = ContinueFuture::makeEmpty();
  statefulOperator.advanceWithFuture(&future);
  ASSERT_FALSE(future.valid());
  ASSERT_EQ(sourcePtr->getOutputCalls(), 1);
}

} // namespace
} // namespace velox4j
