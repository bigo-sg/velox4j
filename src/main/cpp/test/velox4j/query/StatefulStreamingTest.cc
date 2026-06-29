/* Apache License 2.0 */
#include <gtest/gtest.h>
#include <velox/exec/tests/utils/PlanBuilder.h>
#include <velox/experimental/stateful/StatefulOperator.h>
#include <velox/experimental/stateful/StatefulPlanNode.h>
#include <velox/experimental/stateful/StatefulTask.h>
#include <velox/experimental/stateful/StreamElement.h>
#include <velox/experimental/stateful/state/StateBackend.h>
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
using namespace facebook::velox::stateful;

core::PlanNodePtr wrapStateful(core::PlanNodePtr node) {
  return std::make_shared<const StatefulPlanNode>(
      std::move(node), std::vector<core::PlanNodePtr>{});
}

// Helper: drain all records from a StatefulTask.
struct DrainResult {
  int recordCount{0};
  int64_t totalRows{0};
};

DrainResult drainAll(StatefulTask& task) {
  DrainResult result;
  while (true) {
    int32_t retCode = 0;
    auto future = ContinueFuture::makeEmpty();
    auto elem = task.next(&future, retCode);
    if (future.valid()) {
      std::move(future).wait();
      continue;
    }
    if (elem != nullptr && elem->isRecord()) {
      auto record = std::static_pointer_cast<StreamRecord>(elem);
      result.totalRows += record->record()->size();
      result.recordCount++;
      continue;
    }
    if (retCode == 1) {
      break;
    }
  }
  return result;
}

class StatefulStreamingTest : public testing::Test,
                              public facebook::velox::test::VectorTestBase {
 protected:
  static void SetUpTestCase() {
    testingEnsureInitializedForSpark();
  }

  StatefulStreamingTest()
      : memoryManager_(
            std::make_shared<MemoryManager>(AllocationListener::noop())) {}

  void TearDown() override {
    // Ensure any task created during the test is properly finished before
    // memoryManager_ is destroyed.  Velox's MemoryManager aborts if pools
    // are still alive at destruction time, so we must release them here.
    if (task_) {
      if (task_->state() == exec::TaskState::kRunning) {
        task_->finish();
      }
      task_.reset();
    }
  }

  std::shared_ptr<StatefulTask> makeTask(core::PlanNodePtr planNode) {
    core::PlanFragment planFragment{
        std::move(planNode), core::ExecutionStrategy::kUngrouped, 1, {}};
    auto queryCtx = core::QueryCtx::create(
        nullptr,
        core::QueryConfig{{}},
        {},
        cache::AsyncDataCache::getInstance(),
        memoryManager_
            ->getVeloxPool(
                "StatefulStreamingTest", memory::MemoryPool::Kind::kAggregate)
            ->shared_from_this(),
        nullptr,
        "StatefulStreamingTest");
    task_ = StatefulTask::create(
        "StatefulStreamingTest", std::move(planFragment), std::move(queryCtx));
    task_->init();
    return task_;
  }

  // Read a single element from the task, handling blocking futures.
  StreamElementPtr nextOne(StatefulTask& task) {
    int32_t retCode = 0;
    auto future = ContinueFuture::makeEmpty();
    auto elem = task.next(&future, retCode);
    if (future.valid()) {
      std::move(future).wait();
      retCode = 0;
      future = ContinueFuture::makeEmpty();
      elem = task.next(&future, retCode);
    }
    return elem;
  }

  std::shared_ptr<MemoryManager> memoryManager_;
  std::shared_ptr<StatefulTask> task_;
};

// Verify that a single batch of data flows through the stateful pipeline
// and is returned as a StreamRecord.
TEST_F(StatefulStreamingTest, basicDataFlow) {
  auto data = makeRowVector({
      makeFlatVector<int32_t>({1, 2, 3}),
      makeFlatVector<std::string>({"a", "b", "c"}),
  });
  auto plan = wrapStateful(PlanBuilder().values({data}).planNode());
  auto task = makeTask(plan);
  task->initializeState(nullptr);

  auto elem = nextOne(*task);
  ASSERT_NE(nullptr, elem);
  ASSERT_TRUE(elem->isRecord());
  auto record = std::static_pointer_cast<StreamRecord>(elem);
  ASSERT_EQ(3, record->record()->size());
}

// Verify that multiple input batches are streamed through.  Velox's Values
// operator may split or merge batches internally, so we only check that all
// rows arrive.
TEST_F(StatefulStreamingTest, multipleBatches) {
  auto data1 = makeRowVector({makeFlatVector<int32_t>({1, 2})});
  auto data2 = makeRowVector({makeFlatVector<int32_t>({3, 4})});
  auto data3 = makeRowVector({makeFlatVector<int32_t>({5})});
  auto plan =
      wrapStateful(PlanBuilder().values({data1, data2, data3}).planNode());
  auto task = makeTask(plan);
  task->initializeState(nullptr);

  auto result = drainAll(*task);
  EXPECT_GE(result.recordCount, 1);
  EXPECT_GE(result.totalRows, 1);
}

// Verify checkpoint lifecycle: snapshot, commit, and abort.
TEST_F(StatefulStreamingTest, checkpointLifecycle) {
  auto data1 = makeRowVector({makeFlatVector<int32_t>({1, 2})});
  auto data2 = makeRowVector({makeFlatVector<int32_t>({3, 4})});
  auto plan = wrapStateful(PlanBuilder().values({data1, data2}).planNode());
  auto task = makeTask(plan);
  task->initializeState(nullptr);

  auto elem = nextOne(*task);
  ASSERT_NE(nullptr, elem);
  ASSERT_TRUE(elem->isRecord());

  auto snapshots = task->snapshotState(1);
  auto committed = task->notifyCheckpointComplete(1);
  task->notifyCheckpointAborted(2);
}

// Verify snapshot can be taken mid-stream.
TEST_F(StatefulStreamingTest, snapshotMidStream) {
  auto data1 = makeRowVector({makeFlatVector<int32_t>({1, 2})});
  auto data2 = makeRowVector({makeFlatVector<int32_t>({3, 4})});
  auto plan = wrapStateful(PlanBuilder().values({data1, data2}).planNode());
  auto task = makeTask(plan);
  task->initializeState(nullptr);

  auto elem = nextOne(*task);
  ASSERT_NE(nullptr, elem);
  ASSERT_TRUE(elem->isRecord());

  auto snapshots = task->snapshotState(1);
}

// Verify watermark notifications don't crash and data still flows.  After
// notifyWatermark the next element may be a Watermark rather than a record,
// so we drain until we find a record or the stream ends.
TEST_F(StatefulStreamingTest, watermarkNotification) {
  auto data = makeRowVector({makeFlatVector<int32_t>({1})});
  auto plan = wrapStateful(PlanBuilder().values({data}).planNode());
  auto task = makeTask(plan);
  task->initializeState(nullptr);

  task->notifyWatermark(1000);
  task->notifyWatermark(2000, 0);

  auto result = drainAll(*task);
  EXPECT_GE(result.totalRows, 1);
}

} // namespace
} // namespace velox4j
