package org.apache.mesos.elasticsearch.executor.mesos;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.log4j.Logger;
import org.apache.mesos.Executor;
import org.apache.mesos.ExecutorDriver;
import org.apache.mesos.Protos;
import org.apache.mesos.elasticsearch.executor.Configuration;
import org.apache.mesos.elasticsearch.executor.elasticsearch.Launcher;
import org.apache.mesos.elasticsearch.executor.model.PortsModel;
import org.apache.mesos.elasticsearch.executor.model.RunTimeSettings;
import org.apache.mesos.elasticsearch.executor.model.ZooKeeperModel;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.node.Node;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.List;

/**
 * Executor for Elasticsearch.
 */
public class ElasticsearchExecutor implements Executor {
    private final Launcher launcher;
    public static final Logger LOGGER = Logger.getLogger(ElasticsearchExecutor.class.getCanonicalName());
    private final TaskStatus taskStatus;
    private Configuration configuration;

    public ElasticsearchExecutor(Launcher launcher, TaskStatus taskStatus) {
        this.launcher = launcher;
        this.taskStatus = taskStatus;
    }

    @Override
    public void registered(ExecutorDriver driver, Protos.ExecutorInfo executorInfo, Protos.FrameworkInfo frameworkInfo, Protos.SlaveInfo slaveInfo) {
        LOGGER.info("Executor Elasticsearch registered on slave " + slaveInfo.getHostname());
    }

    @Override
    public void reregistered(ExecutorDriver driver, Protos.SlaveInfo slaveInfo) {
        LOGGER.info("Executor Elasticsearch re-registered on slave " + slaveInfo.getHostname());
    }

    @Override
    public void disconnected(ExecutorDriver driver) {
        LOGGER.info("Executor Elasticsearch disconnected");
    }

    @Override
    public void launchTask(final ExecutorDriver driver, final Protos.TaskInfo task) {
        LOGGER.info("Starting executor with a TaskInfo of:");
        LOGGER.info(task.toString());

        Protos.TaskID taskID = task.getTaskId();
        taskStatus.setTaskID(taskID);

        // Send status update, starting
        driver.sendStatusUpdate(taskStatus.starting());

        try {
            // Parse CommandInfo arguments
            List<String> list = task.getExecutor().getCommand().getArgumentsList();
            String[] args = list.toArray(new String[list.size()]);
            LOGGER.debug("Using arguments: " + Arrays.toString(args));
            configuration = new Configuration(args);

            // Add settings provided in es Settings file
            URL elasticsearchSettingsPath = java.net.URI.create(configuration.getElasticsearchSettingsLocation()).toURL();
            LOGGER.debug("Using elasticsearch settings file: " + elasticsearchSettingsPath);
            ImmutableSettings.Builder esSettings = ImmutableSettings.builder().loadFromUrl(elasticsearchSettingsPath);
            launcher.addRuntimeSettings(esSettings);

            // Parse ports
            RunTimeSettings ports = new PortsModel(task);
            launcher.addRuntimeSettings(ports.getRuntimeSettings());

            // Parse ZooKeeper address
            RunTimeSettings zk = new ZooKeeperModel(configuration.getElasticsearchZKURL());
            launcher.addRuntimeSettings(zk.getRuntimeSettings());

            // Launch Node
            final Node node = launcher.launch();

            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    // Send status update, finished
                    driver.sendStatusUpdate(taskStatus.finished());
                    node.close();
                }
            }));

            // Send status update, running
            driver.sendStatusUpdate(taskStatus.running());
        } catch (InvalidParameterException | MalformedURLException e) {
            driver.sendStatusUpdate(taskStatus.failed());
            LOGGER.error(e);
        }
    }

    @Override
    public void killTask(ExecutorDriver driver, Protos.TaskID taskId) {
        LOGGER.info("Kill task: " + taskId.getValue());
        driver.sendStatusUpdate(taskStatus.failed());
    }

    @Override
    public void frameworkMessage(ExecutorDriver driver, byte[] data) {
        try {
            Protos.HealthCheck healthCheck = Protos.HealthCheck.parseFrom(data);
            LOGGER.info("HealthCheck request received: " + healthCheck.toString());
            driver.sendStatusUpdate(taskStatus.currentState());
        } catch (InvalidProtocolBufferException e) {
            LOGGER.debug("Unable to parse framework message as HealthCheck", e);
        }
    }

    @Override
    public void shutdown(ExecutorDriver driver) {
        LOGGER.info("Shutting down framework...");
    }

    @Override
    public void error(ExecutorDriver driver, String message) {
        LOGGER.info("Error in executor: " + message);
    }
}
