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

package com.nvidia.spark.rapids.shims.v2

import com.nvidia.spark.rapids.{ExecChecks, ExecRule, GpuExec, SparkPlanMeta, SparkShims, TypeSig}
import com.nvidia.spark.rapids.GpuOverrides.exec
import org.apache.hadoop.fs.FileStatus

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.plans.physical.BroadcastMode
import org.apache.spark.sql.catalyst.util.{DateFormatter, DateTimeUtils}
import org.apache.spark.sql.execution.SparkPlan
import org.apache.spark.sql.execution.adaptive.{AdaptiveSparkPlanExec, CustomShuffleReaderExec, QueryStageExec}
import org.apache.spark.sql.execution.datasources.PartitioningAwareFileIndex
import org.apache.spark.sql.execution.exchange.BroadcastExchangeExec
import org.apache.spark.sql.execution.joins.{BroadcastHashJoinExec, BroadcastNestedLoopJoinExec, ShuffledHashJoinExec}
import org.apache.spark.sql.internal.SQLConf
import org.apache.spark.sql.rapids.execution.GpuCustomShuffleReaderExec

/**
* Shim base class that can be compiled with every supported 3.0.x
*/
trait Spark30XShims extends SparkShims {
  override def parquetRebaseReadKey: String =
    SQLConf.LEGACY_PARQUET_REBASE_MODE_IN_READ.key
  override def parquetRebaseWriteKey: String =
    SQLConf.LEGACY_PARQUET_REBASE_MODE_IN_WRITE.key
  override def avroRebaseReadKey: String =
    SQLConf.LEGACY_AVRO_REBASE_MODE_IN_READ.key
  override def avroRebaseWriteKey: String =
    SQLConf.LEGACY_AVRO_REBASE_MODE_IN_WRITE.key
  override def parquetRebaseRead(conf: SQLConf): String =
    conf.getConf(SQLConf.LEGACY_PARQUET_REBASE_MODE_IN_READ)
  override def parquetRebaseWrite(conf: SQLConf): String =
    conf.getConf(SQLConf.LEGACY_PARQUET_REBASE_MODE_IN_WRITE)

  override def sessionFromPlan(plan: SparkPlan): SparkSession = {
    plan.sqlContext.sparkSession
  }

  override def filesFromFileIndex(
      fileIndex: PartitioningAwareFileIndex
  ): Seq[FileStatus] = {
    fileIndex.allFiles()
  }

  def broadcastModeTransform(mode: BroadcastMode, rows: Array[InternalRow]): Any =
    mode.transform(rows)

  override def getDateFormatter(): DateFormatter = {
    DateFormatter(DateTimeUtils.getZoneId(SQLConf.get.sessionLocalTimeZone))
  }

  override def isExchangeOp(plan: SparkPlanMeta[_]): Boolean = {
    // if the child query stage already executed on GPU then we need to keep the
    // next operator on GPU in these cases
    SQLConf.get.adaptiveExecutionEnabled && (plan.wrapped match {
      case _: CustomShuffleReaderExec
           | _: ShuffledHashJoinExec
           | _: BroadcastHashJoinExec
           | _: BroadcastExchangeExec
           | _: BroadcastNestedLoopJoinExec => true
      case _ => false
    })
  }

  override def isAqePlan(p: SparkPlan): Boolean = p match {
    case _: AdaptiveSparkPlanExec |
         _: QueryStageExec |
         _: CustomShuffleReaderExec => true
    case _ => false
  }

  override def isCustomReaderExec(x: SparkPlan): Boolean = x match {
    case _: GpuCustomShuffleReaderExec | _: CustomShuffleReaderExec => true
    case _ => false
  }

  override def aqeShuffleReaderExec: ExecRule[_ <: SparkPlan] = exec[CustomShuffleReaderExec](
    "A wrapper of shuffle query stage",
    ExecChecks((TypeSig.commonCudfTypes + TypeSig.NULL + TypeSig.DECIMAL_64 + TypeSig.ARRAY +
        TypeSig.STRUCT + TypeSig.MAP).nested(), TypeSig.all),
    (exec, conf, p, r) => new GpuCustomShuffleReaderMeta(exec, conf, p, r))
}
