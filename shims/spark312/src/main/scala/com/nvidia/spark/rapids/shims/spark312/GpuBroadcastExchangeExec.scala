/*
 * Copyright (c) 2021, NVIDIA CORPORATION.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nvidia.spark.rapids.shims.spark312

import java.util.UUID

import com.nvidia.spark.rapids.GpuMetric

import org.apache.spark.sql.catalyst.plans.logical.Statistics
import org.apache.spark.sql.catalyst.plans.physical.BroadcastMode
import org.apache.spark.sql.execution.SparkPlan
import org.apache.spark.sql.execution.exchange.BroadcastExchangeLike
import org.apache.spark.sql.rapids.execution.GpuBroadcastExchangeExecBaseWithFuture

case class GpuBroadcastExchangeExec(
    override val mode: BroadcastMode,
    child: SparkPlan) extends GpuBroadcastExchangeExecBaseWithFuture(mode, child)
    with BroadcastExchangeLike {

  override def runId: UUID = _runId

  override def runtimeStatistics: Statistics = {
    Statistics(
      sizeInBytes = metrics("dataSize").value,
      rowCount = Some(metrics(GpuMetric.NUM_OUTPUT_ROWS).value))
  }

  override def doCanonicalize(): SparkPlan = {
    GpuBroadcastExchangeExec(mode.canonicalized, child.canonicalized)
  }
}
