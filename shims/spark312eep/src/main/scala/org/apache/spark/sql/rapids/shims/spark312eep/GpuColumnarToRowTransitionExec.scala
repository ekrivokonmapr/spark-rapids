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

package org.apache.spark.sql.rapids.shims.spark312eep

import com.nvidia.spark.rapids.GpuColumnarToRowExecParent

import org.apache.spark.sql.catalyst.expressions.NamedExpression
import org.apache.spark.sql.execution.{ColumnarToRowTransition, SparkPlan}

case class GpuColumnarToRowTransitionExec(child: SparkPlan,
    override val exportColumnarRdd: Boolean = false,
    override val postProjection: Seq[NamedExpression] = Seq.empty)
    extends GpuColumnarToRowExecParent(child, exportColumnarRdd, postProjection)
        with ColumnarToRowTransition
