package org.apache.mesos.elasticsearch.scheduler.cluster;

import org.apache.mesos.Protos;
import org.apache.mesos.elasticsearch.scheduler.state.ESState;
import org.apache.mesos.elasticsearch.scheduler.state.FrameworkState;

import javax.validation.constraints.NotNull;

/**
 * A wrapper class to hold ES task info and ES task status
 */
public class ESTask {
    public static final String TASK_INFO_KEY = "TaskInfo";
    public static final String TASK_STATUS_KEY = "TaskStatus";
    private final ESState<Protos.TaskInfo> taskInfoState;
    private final ESState<Protos.TaskStatus> taskStatusState;

    public ESTask(@NotNull ESState<Protos.TaskInfo> taskInfoState, @NotNull ESState<Protos.TaskStatus> taskStatusState, @NotNull Protos.TaskInfo taskInfo) {
        this.taskInfoState = taskInfoState;
        this.taskStatusState = taskStatusState;
        if (isNewTask()) {
            setTask(taskInfo);
            taskStatusState.set(ClusterStateUtil.getDefaultTaskStatus(taskInfo));
        }
    }

    public Protos.TaskInfo getTask() {
        return taskInfoState.get();
    }

    public Protos.TaskStatus getStatus() {
        final Protos.TaskStatus taskStatus = taskStatusState.get();
        return taskStatus == null ? ClusterStateUtil.getDefaultTaskStatus(getTask()) : taskStatus;
    }

    public void updateStatus(@NotNull Protos.TaskStatus taskStatus) {
        if (getStatus() == null || !getStatus().equals(taskStatus)) {
            taskStatusState.set(taskStatus);
        }

    }

    public void destroy() {
        taskInfoState.destroy();
        taskStatusState.destroy();
    }

    public static String getTaskKey(FrameworkState frameworkState, Protos.TaskInfo taskInfo) {
        return getKey(frameworkState, TASK_INFO_KEY, taskInfo);
    }

    public static String getStatusKey(FrameworkState frameworkState, Protos.TaskInfo taskInfo) {
        return getKey(frameworkState, TASK_STATUS_KEY, taskInfo);
    }

    private static String getKey(FrameworkState frameworkState, String key, Protos.TaskInfo taskInfo) {
        return frameworkState.getFrameworkID().getValue() + "/" + key + "/" + taskInfo.getTaskId().getValue();
    }


    @Override
    public boolean equals(Object obj) {
        return obj instanceof ESTask && ((ESTask) obj).getStatus().equals(this.getStatus()) && ((ESTask) obj).getTask().equals(this.getTask());
    }

    @Override
    public int hashCode() {
        return this.getTask().hashCode() + this.getStatus().hashCode();
    }

    @Override
    public String toString() {
        return getTask().toString() + " " + getStatus().toString();
    }

    private void setTask(Protos.TaskInfo taskInfo) {
        taskInfoState.set(taskInfo);
    }

    private boolean isNewTask() {
        return getTask() == null;
    }
}