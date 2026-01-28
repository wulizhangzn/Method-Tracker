package com.znzhang.timetracker.listener;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.znzhang.timetracker.service.AgentCommunicationService;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public class RunExecutionListener implements ExecutionListener {
    private final Project project;

    private static boolean isRunning = false;

    public RunExecutionListener(Project project) {
        this.project = project;
    }

    @Override
    public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) {
        if (!isRunning) {
            return;
        }
        RunProfile profile = env.getRunProfile();
        // 检查是否为标准的 Java 运行配置
        boolean isJavaApp = profile instanceof com.intellij.execution.configurations.JavaRunConfigurationModule ||
                profile.getClass().getSimpleName().contains("ApplicationConfiguration");

        // 检查是否为 Spring Boot
        boolean isSpringBoot = profile.getClass().getName().contains("SpringBoot");
        if (!isJavaApp && !isSpringBoot) {
            return;
        }
        // 当用户点击 Run/Debug，程序启动后，延迟 2 秒尝试连接
        // 延迟是为了给 Agent 留出启动并创建 .port 文件的时间
        AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
            AgentCommunicationService service = project.getService(AgentCommunicationService.class);
            if (service != null) {
                service.connect();
            }
        }, 2, TimeUnit.SECONDS);
    }

    public static void open() {
        isRunning = true;
    }

    public static void close() {
        isRunning = false;
    }
}
