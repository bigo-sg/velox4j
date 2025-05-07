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

#include "StatefulQueryExecutor.h"
#include <velox/experimental/stateful/StatefulTask.h>
#include "velox4j/query/Query.h"

#include <iostream>
#include <utility>
//#include <gperftools/heap-profiler.h>

namespace velox4j {

using namespace facebook::velox;

namespace {

class Out : public UpIterator {
 public:
  Out(MemoryManager* memoryManager, std::shared_ptr<const Query> query)
      : memoryManager_(memoryManager), query_(std::move(query)) {
    static std::atomic<uint32_t> executionId{
        0}; // Velox query ID, same with taskId.
    const uint32_t eid = executionId++;
    std::cout << "Stateful velox plan is " << query_->plan()->toString(true, true) << std::endl;
    VLOG(2)
        << "Velox plan is " << query_->plan()->toString(true, true);
    core::PlanFragment planFragment{
        query_->plan(), core::ExecutionStrategy::kUngrouped, 1, {}};
    std::shared_ptr<core::QueryCtx> queryCtx = core::QueryCtx::create(
        nullptr,
        core::QueryConfig{query_->queryConfig()->toMap()},
        query_->connectorConfig()->toMap(),
        cache::AsyncDataCache::getInstance(),
        memoryManager_
            ->getVeloxPool(
                fmt::format("Query Memory Pool - EID {}", std::to_string(eid)),
                memory::MemoryPool::Kind::kAggregate)
            ->shared_from_this(),
        nullptr,
        fmt::format("Query Context - EID {}", std::to_string(eid)));

    auto task = stateful::StatefulTask::create(
        fmt::format("Task - EID {}", std::to_string(eid)),
        std::move(planFragment),
        std::move(queryCtx));

    std::cout << "Stateful query task created" << std::endl;
    std::unordered_set<core::PlanNodeId> planNodesWithSplits{};
    for (const auto& boundSplit : query_->boundSplits()) {
      exec::Split split = *boundSplit->split();
      planNodesWithSplits.emplace(boundSplit->planNodeId());
      task->addSplit(boundSplit->planNodeId(), std::move(split));
    }
    for (const auto& nodeWithSplits : planNodesWithSplits) {
      task->noMoreSplits(nodeWithSplits);
    }

    task_ = task;
  }

  ~Out() override {
    if (task_ != nullptr && task_->isRunning()) {
      // TODO: add a method to finish the task and set state.
      task_->finish();
      // FIXME: Calling .wait() may take no effect in single thread execution
      //  mode.
      task_->requestCancel().wait();
    }
    task_.reset();
    std::cout << "StatefulQueryExecutor dessc" << std::endl;
  }

  State advance() override {
    VELOX_CHECK_NULL(pending_);
    return advance0();
  }

  void wait() override {
  }

  RowVectorPtr get() override {
    VELOX_CHECK_NOT_NULL(
        pending_,
        "Out: No pending row vector to return.  No pending row vector to return. Make sure the iterator is available via member function advance() first");
    const auto out = pending_;
    pending_ = nullptr;
    return out;
  }

 private:

  State advance0() {
    //std::cout << "Advance enter " << wait << std::endl;
    int32_t retCode = 0;
    auto out = task_->next(retCode);
    //std::cout << "Advance next " << wait << std::endl;
    if (out != nullptr) {
      pending_ = std::move(out);
      return State::AVAILABLE;
    }
    if (retCode == 1) {
      return State::FINISHED;
    }
    return State::BLOCKED;
  }

  MemoryManager* const memoryManager_;
  std::shared_ptr<const Query> query_;
  std::shared_ptr<stateful::StatefulTask> task_;
  bool hasPendingState_{false};
  State pendingState_{State::BLOCKED};
  RowVectorPtr pending_{nullptr};
  int count = 0;
};
} // namespace

StatefulQueryExecutor::StatefulQueryExecutor(MemoryManager* memoryManager, std::shared_ptr<const Query> query)
    : memoryManager_(memoryManager), query_(query) {}

std::unique_ptr<UpIterator> StatefulQueryExecutor::execute() const {
  return std::make_unique<Out>(memoryManager_, query_);
}
} // namespace velox4j
