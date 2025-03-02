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

package com.nvidia.spark.rapids.shims.spark304

import com.nvidia.spark.rapids._

import org.apache.spark.sql.catalyst.expressions.Expression
import org.apache.spark.sql.catalyst.plans.JoinType
import org.apache.spark.sql.execution.SparkPlan
import org.apache.spark.sql.execution.joins.{BuildLeft, BuildRight, BuildSide, ShuffledHashJoinExec}
import org.apache.spark.sql.rapids.execution.{GpuHashJoin, JoinTypeChecks}

object GpuJoinUtils {
  def getGpuBuildSide(buildSide: BuildSide): GpuBuildSide = {
    buildSide match {
      case BuildRight => GpuBuildRight
      case BuildLeft => GpuBuildLeft
      case _ => throw new Exception("unknown buildSide Type")
    }
  }
}

class GpuShuffledHashJoinMeta(
    join: ShuffledHashJoinExec,
    conf: RapidsConf,
    parent: Option[RapidsMeta[_, _, _]],
    rule: DataFromReplacementRule)
  extends SparkPlanMeta[ShuffledHashJoinExec](join, conf, parent, rule) {
  val leftKeys: Seq[BaseExprMeta[_]] =
    join.leftKeys.map(GpuOverrides.wrapExpr(_, conf, Some(this)))
  val rightKeys: Seq[BaseExprMeta[_]] =
    join.rightKeys.map(GpuOverrides.wrapExpr(_, conf, Some(this)))
  val condition: Option[BaseExprMeta[_]] =
    join.condition.map(GpuOverrides.wrapExpr(_, conf, Some(this)))

  override val childExprs: Seq[BaseExprMeta[_]] = leftKeys ++ rightKeys ++ condition

  override val namedChildExprs: Map[String, Seq[BaseExprMeta[_]]] =
    JoinTypeChecks.equiJoinMeta(leftKeys, rightKeys, condition)

  override def tagPlanForGpu(): Unit = {
    GpuHashJoin.tagJoin(this, join.joinType, join.leftKeys, join.rightKeys, join.condition)
  }

  override def convertToGpu(): GpuExec = {
    val Seq(left, right) = childPlans.map(_.convertIfNeeded())
    val joinExec = GpuShuffledHashJoinExec(
      leftKeys.map(_.convertToGpu()),
      rightKeys.map(_.convertToGpu()),
      join.joinType,
      GpuJoinUtils.getGpuBuildSide(join.buildSide),
      None,
      left,
      right,
      isSkewJoin = false)
    // The GPU does not yet support conditional joins, so conditions are implemented
    // as a filter after the join when possible.
    condition.map(c => GpuFilterExec(c.convertToGpu(), joinExec)).getOrElse(joinExec)
  }
}

case class GpuShuffledHashJoinExec(
    leftKeys: Seq[Expression],
    rightKeys: Seq[Expression],
    joinType: JoinType,
    buildSide: GpuBuildSide,
    override val condition: Option[Expression],
    left: SparkPlan,
    right: SparkPlan,
    override val isSkewJoin: Boolean)
  extends GpuShuffledHashJoinBase(
    leftKeys,
    rightKeys,
    buildSide,
    condition,
    isSkewJoin = isSkewJoin)
