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
#include "velox4j/query/Query.h"

#include <iostream>
#include <utility>

namespace velox4j {

using namespace facebook::velox;

StatefulSerialTask::StatefulSerialTask(
    MemoryManager* memoryManager,
    std::shared_ptr<const Query> query)
    : memoryManager_(memoryManager), query_(std::move(query)) {
  static std::atomic<uint32_t> executionId{
      0}; // Velox query ID, same with taskId.
  const uint32_t eid = executionId++;
  core::PlanFragment planFragment{
      query_->plan(), core::ExecutionStrategy::kUngrouped, 1, {}};
  std::cout << "Stateful velox plan is " << query_->plan()->toString(true, true) << std::endl;
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

  task_ = task;
  task_->initOperators();
}

StatefulSerialTask::~StatefulSerialTask() {
  if (task_ != nullptr && task_->isRunning()) {
    // TODO: add a method to finish the task and set state.
    task_->finish();
    // FIXME: Calling .wait() may take no effect in single thread execution
    //  mode.
    task_->requestCancel().wait();
  }
  task_.reset();
}

UpIterator::State StatefulSerialTask::advance() {
  VELOX_CHECK_NULL(pending_);
  return advance0(false);
}

void StatefulSerialTask::wait() {
}

RowVectorPtr StatefulSerialTask::get() {
  VELOX_CHECK(false, "Should not call get for stateful task.");
  return nullptr;
}

stateful::StreamElementPtr StatefulSerialTask::statefulGet() {
  VELOX_CHECK_NOT_NULL(
      pending_,
      "SerialTask: No pending row vector to return. Make sure the "
      "iterator is available via member function advance() first");
  const auto out = std::move(pending_);
  pending_ = nullptr;
  return out;
}

void StatefulSerialTask::notifyWatermark(long watermark, int index) {
  task_->notifyWatermark(watermark, index);
}

void StatefulSerialTask::addSplit(
    const core::PlanNodeId& planNodeId,
    int32_t groupId,
    std::shared_ptr<connector::ConnectorSplit> connectorSplit) {
  auto cs = connectorSplit;
  task_->addSplit(planNodeId, exec::Split{std::move(cs), groupId});
}

void StatefulSerialTask::noMoreSplits(const core::PlanNodeId& planNodeId) {
  task_->noMoreSplits(planNodeId);
}

std::unique_ptr<SerialTaskStats> StatefulSerialTask::collectStats() {
  const auto stats = task_->taskStats();
  return std::make_unique<SerialTaskStats>(stats);
}

UpIterator::State StatefulSerialTask::advance0(bool wait) {
  while (true) {
    int32_t retCode = 0;
    auto out = task_->next(retCode);
    if (out != nullptr) {
      pending_ = std::move(out);
      return State::AVAILABLE;
    }
    if (retCode == 1) {
      return State::FINISHED;
    }
    return State::BLOCKED;
  }
}

StatefulQueryExecutor::StatefulQueryExecutor(
    MemoryManager* memoryManager,
    std::shared_ptr<const Query> query)
    : memoryManager_(memoryManager), query_(query) {}

std::unique_ptr<StatefulSerialTask> StatefulQueryExecutor::execute() const {
  return std::make_unique<StatefulSerialTask>(memoryManager_, query_);
}
} // namespace velox4j
