// Copyright (c) YugaByte, Inc.

package com.yugabyte.yw.commissioner.tasks;

import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.yugabyte.yw.commissioner.AbstractTaskBase;
import com.yugabyte.yw.commissioner.Common.CloudType;
import com.yugabyte.yw.commissioner.TaskList;
import com.yugabyte.yw.commissioner.TaskListQueue;
import com.yugabyte.yw.commissioner.tasks.params.ITaskParams;
import com.yugabyte.yw.commissioner.tasks.params.UniverseTaskParams;
import com.yugabyte.yw.commissioner.tasks.subtasks.AnsibleDestroyServer;
import com.yugabyte.yw.models.Universe;
import com.yugabyte.yw.models.Universe.NodeDetails;
import com.yugabyte.yw.models.Universe.UniverseDetails;
import com.yugabyte.yw.models.Universe.UniverseUpdater;

public abstract class UniverseTaskBase extends AbstractTaskBase {
  public static final Logger LOG = LoggerFactory.getLogger(UniverseTaskBase.class);

  // Flag to indicate if we have locked the universe.
  private boolean universeLocked = false;

  // The threadpool on which the tasks are executed.
  protected ExecutorService executor;

  // The sequence of task lists that should be executed.
  protected TaskListQueue taskListQueue;

  // The task params.
  protected UniverseTaskParams taskParams() {
    return (UniverseTaskParams)taskParams;
  }

  @Override
  public void initialize(ITaskParams params) {
    super.initialize(params);
    // Create the threadpool for the subtasks to use.
    int numThreads = 10;
    ThreadFactory namedThreadFactory =
        new ThreadFactoryBuilder().setNameFormat("TaskPool-" + getName() + "-%d").build();
    executor = Executors.newFixedThreadPool(numThreads, namedThreadFactory);
  }

  @Override
  public String getName() {
    return super.getName() + "(" + taskParams().universeUUID + ")";
  }

  @Override
  public int getPercentCompleted() {
    if (taskListQueue == null) {
      return 0;
    }
    return taskListQueue.getPercentCompleted();
  }

  /**
   * Locks the universe for updates by setting the 'updateInProgress' flag. If the universe is
   * already being modified, then throws an exception.
   */
  public Universe lockUniverseForUpdate() {
    // Create the update lambda.
    UniverseUpdater updater = new UniverseUpdater() {
      @Override
      public void run(Universe universe) {
        UniverseDetails universeDetails = universe.universeDetails;
        // If this universe is already being edited, fail the request.
        if (universeDetails.updateInProgress) {
          String msg = "UserUniverse " + taskParams().universeUUID + " is already being updated.";
          LOG.error(msg);
          throw new RuntimeException(msg);
        }

        // Persist the updated information about the universe. Mark it as being edited.
        universeDetails.updateInProgress = true;
        universeDetails.updateSucceeded = false;
      }
    };
    // Perform the update. If unsuccessful, this will throw a runtime exception which we do not
    // catch as we want to fail.
    Universe universe = Universe.save(taskParams().universeUUID, updater);
    universeLocked = true;
    LOG.debug("Locked universe " + taskParams().universeUUID + " for updates");
    // Return the universe object that we have already updated.
    return universe;
  }

  public void unlockUniverseForUpdate() {
    if (!universeLocked) {
      LOG.warn("Unlock universe called when it was not locked.");
      return;
    }
    // Create the update lambda.
    UniverseUpdater updater = new UniverseUpdater() {
      @Override
      public void run(Universe universe) {
        UniverseDetails universeDetails = universe.universeDetails;
        // If this universe is not being edited, fail the request.
        if (!universeDetails.updateInProgress) {
          String msg = "UserUniverse " + taskParams().universeUUID + " is not being edited.";
          LOG.error(msg);
          throw new RuntimeException(msg);
        }
        // Persist the updated information about the universe. Mark it as being edited.
        universeDetails.updateInProgress = false;
      }
    };
    // Perform the update. If unsuccessful, this will throw a runtime exception which we do not
    // catch as we want to fail.
    Universe.save(taskParams().universeUUID, updater);
    LOG.debug("Unlocked universe " + taskParams().universeUUID + " for updates");
  }

  /**
   * Creates a task list to destroy nodes and adds it to the task queue.
   *
   * @param nodes : a collection of nodes that need to be destroyed
   */
  public void createDestroyServerTasks(Collection<NodeDetails> nodes) {
    TaskList taskList = new TaskList("AnsibleDestroyServers", executor);
    for (NodeDetails node : nodes) {
      AnsibleDestroyServer.Params params = new AnsibleDestroyServer.Params();
      // Set the cloud name.
      params.cloud = CloudType.aws;
      // Add the node name.
      params.nodeName = node.instance_name;
      // Add the universe uuid.
      params.universeUUID = taskParams().universeUUID;
      // Create the Ansible task to destroy the server.
      AnsibleDestroyServer task = new AnsibleDestroyServer();
      task.initialize(params);
      // Add it to the task list.
      taskList.addTask(task);
    }
    taskListQueue.add(taskList);
  }
}