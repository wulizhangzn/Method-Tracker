package com.znzhang.methodtracker.service;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import com.znzhang.methodtracker.MySpaceSettings;
import com.znzhang.methodtracker.context.AgentDataContext;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service(Service.Level.PROJECT)
public final class AgentCommunicationService implements Disposable {

    private Socket socket;
    private PrintWriter printWriter;
    private final Project project;
    private volatile boolean isRunning = true;

    // 定义一个消息主题，用于在插件内部传递收到的耗时数据
    public static final Topic<AgentDataListener> DATA_TOPIC = Topic.create("AgentDataTopic", AgentDataListener.class);
    public static final Topic<AgentEndListener> END_TOPIC = Topic.create("AgentEndTopic", AgentEndListener.class);

    public interface AgentDataListener {
        void onDataReceived(String data);
    }

    public interface AgentEndListener {
        void onDataReceived(String data);
    }

    public AgentCommunicationService(Project project) {
        this.project = project;
    }

    public void connect() {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Connecting to Agent") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    File portFile = new File(System.getProperty("user.home"), ".myspace_agent.port");
                    // 等待端口文件生成
                    int attempts = 0;
                    while (!portFile.exists() && attempts < 10) {
                        TimeUnit.SECONDS.sleep(1);
                        attempts++;
                    }
                    if (!portFile.exists()) return;
                    int port = Integer.parseInt(Files.readString(portFile.toPath()).trim());
                    socket = new Socket("127.0.0.1", port);
                    AgentDataContext.clearData();
                    printWriter = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                    syncMethodsWithAgent("SET_METHODS:"); // 连上后立即同步一次已存的方法
                    startReading();
                } catch (Exception e) {
                    NotificationGroupManager.getInstance()
                            .getNotificationGroup("MySpaceMonitorGroup")
                            .createNotification("Unable to connect to the Agent: " + e.getMessage(), NotificationType.ERROR)
                            .notify(project);
                }
            }
        });
    }

    public void syncMethodsWithAgent(String action) {
        if (printWriter != null && !socket.isClosed()) {
            MySpaceSettings.State state = MySpaceSettings.getInstance(project).getState();
            if (state == null) {
                return;
            }
            Set<String> methods = Objects.requireNonNull(state).monitoredMethods;
            printWriter.println(action + String.join("&&", methods));
        }
    }

    private void startReading() {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while (isRunning && (line = in.readLine()) != null) {
                    if (line.startsWith("DATA:")) {
                        String data = line.substring(5);
                        // 通过消息总线广播数据
                        project.getMessageBus().syncPublisher(DATA_TOPIC).onDataReceived(data);
                    } else if (line.startsWith("END:")) {
                        project.getMessageBus().syncPublisher(END_TOPIC).onDataReceived(line.substring(4));
                    }
                }
            } catch (IOException ignored) {
            }
        });
    }

    @Override
    public void dispose() {
        isRunning = false;
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
    }
}