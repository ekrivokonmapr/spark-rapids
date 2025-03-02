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
package com.nvidia.spark.rapids.shims.spark301emr

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.catalyst.plans.logical.Statistics
import org.apache.spark.sql.catalyst.plans.physical.Partitioning
import org.apache.spark.sql.execution.{ShufflePartitionSpec, SparkPlan}
import org.apache.spark.sql.execution.exchange.ShuffleExchangeLike
import org.apache.spark.sql.rapids.execution.GpuShuffleExchangeExecBaseWithMetrics

case class GpuShuffleExchangeExec(
    override val outputPartitioning: Partitioning,
    child: SparkPlan,
    canChangeNumPartitions: Boolean)
  extends GpuShuffleExchangeExecBaseWithMetrics(outputPartitioning, child)
      with ShuffleExchangeLike {

  override def numMappers: Int = shuffleDependencyColumnar.rdd.getNumPartitions

  override def numPartitions: Int = shuffleDependencyColumnar.partitioner.numPartitions

  override def getShuffleRDD(partitionSpecs: Array[ShufflePartitionSpec]): RDD[_] = {
    throw new UnsupportedOperationException
  }

  override def runtimeStatistics: Statistics = {
    // note that Spark will only use the sizeInBytes statistic but making the rowCount
    // available here means that we can more easily reference it in GpuOverrides when
    // planning future query stages when AQE is on
    Statistics(
      sizeInBytes = metrics("dataSize").value,
      rowCount = Some(metrics("numOutputRows").value)
    )
  }
}
