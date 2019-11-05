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

package org.apache.spark.scheduler

import org.apache.spark.scheduler.SchedulingMode.SchedulingMode
import org.apache.spark.storage.BlockManagerId
import org.apache.spark.util.AccumulatorV2

/**
 * Low-level task scheduler interface, currently implemented exclusively by
 * [[org.apache.spark.scheduler.TaskSchedulerImpl]].
 * This interface allows plugging插入 in different task schedulers. Each TaskScheduler schedules tasks
 * for a single SparkContext. These schedulers get sets of tasks submitted to them from the
 * DAGScheduler for each stage, and are responsible for sending the tasks to the cluster, running
 * them, retrying if there are failures, and mitigating stragglers. They return events to the
 * DAGScheduler.
 *
 * taskscheduler的的对象（org.apache.spark.scheduler.TaskScheduler）
 * 是一个trait（Scala的叫法，简单的理解为类似于java的interface），
 * 这是因为task的提交方式有多种，
 * 可以是yarn－client模式，
 * 也可以是yarn－cluster模型，
 * 这取决于提交spark提交时候设置的参数master
 */
private[spark] trait TaskScheduler {
  // 定义一个任务id
  private val appId = "spark-application-" + System.currentTimeMillis
  // 根调度池
  def rootPool: Pool
  // 调度模式。调度模式有先进先出模式（FIFO）和公平调度模式（FAIR），详见SchedulingMode枚举类
  def schedulingMode: SchedulingMode

  def start(): Unit

  // Invoked调用 after system has successfully initialized (typically in spark context).//在系统成功初始化之后（通常是在spark context中），
  // Yarn uses this to bootstrap allocation分配 of resources based on preferred locations首选位置, //Yarn使用这个方法根据首选位置来分配资源，
  // wait for slave registrationszhuce, etc.//等待系统slave注册等
  def postStartHook() { }

  // Disconnect from the cluster.// 从集群断开链接
  def stop(): Unit

  // Submit a sequence of tasks to run.提交待运行的task队列
  def submitTasks(taskSet: TaskSet): Unit

  // Cancel a stage.杀死一个Stage中的所有任务，使该Stage和依赖该Stage的所有task失败。如果后端不支持kill任务，则引发unsupportedOperationException。
  def cancelTasks(stageId: Int, interruptThread: Boolean): Unit

  /**
   * Kills a task attempt.
   * 终止任务尝试。如果后端不支持终止任务，则抛出UnsupportedOperationException。
   * @return Whether the task was successfully killed.
   * Spark 2.4.0 多了一个 def killAllTaskAttempts(stageId: Int, interruptThread: Boolean, reason: String): Unit
   *                      终止一个stage中的所有运行中的任务尝试，如果不支持终止任务，则抛出UnsupportedOperationException。
   */
  def killTaskAttempt(taskId: Long, interruptThread: Boolean, reason: String): Boolean

  // Set the DAG scheduler for upcalls 之前赋值. This is guaranteed to be set before submitTasks is called赋值.//在调用前为DAG调度器赋值，这个是为了保证在调submitTasks方法前赋值。
  def setDAGScheduler(dagScheduler: DAGScheduler): Unit

  // Get the default level of parallelism to use in the cluster, as a hint for sizing jobs.// 获取要在集群中使用的默认并行级别，作为调整作业大小的提示。
  def defaultParallelism(): Int

  /**
   * Update metrics for in-progress tasks and let the master know that the BlockManager is still
   * alive. Return true if the driver knows about the given block manager. Otherwise, return false,
   * indicating that the block manager should re-register.    //excutor心跳接收器
   */
  def executorHeartbeatReceived(
      execId: String,
      accumUpdates: Array[(Long, Seq[AccumulatorV2[_, _]])],
      blockManagerId: BlockManagerId): Boolean

  /**
   * Get an application ID associated with the job.
   * @return An application ID  // 获取和job关联的application ID
   */
  def applicationId(): String = appId

  /**
   * Process a lost executor    // 处理丢失的executor
   * Spark 2.4.0 多了一个  处理移除的worker
   *  //def workerRemoved(workerId: String, host: String, message: String): Unit
   */
  def executorLost(executorId: String, reason: ExecutorLossReason): Unit

  /**
   * Get an application's attempt ID associated with the job.
   * @return An application's Attempt ID    // 获取和job关联的application的重试ID
   */
  def applicationAttemptId(): Option[String]

}
