package com.znzhang.timetracker.extension;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.runners.JavaProgramPatcher;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.znzhang.timetracker.MySpaceSettings;
import com.znzhang.timetracker.listener.RunExecutionListener;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class AgentPatcher extends JavaProgramPatcher {

    @Override
    public void patchJavaParameters(Executor executor, RunProfile runProfile, JavaParameters javaParameters) {
        RunExecutionListener.close();
        Project project = null;
        if (runProfile instanceof RunConfigurationBase) {
            project = ((RunConfigurationBase<?>) runProfile).getProject();
        }
        PluginId pluginId = PluginId.getId("com.znzhang.time-tracker");
        File pluginPath = Objects.requireNonNull(PluginManagerCore.getPlugin(pluginId)).getPluginPath().toFile();
        File agentJar = new File(pluginPath, "lib/timeWatch.jar");
        ParametersList vmParametersList = javaParameters.getVMParametersList();
        List<String> parameters = vmParametersList.getParameters();
        boolean notExistsMyAgent = parameters.stream().noneMatch(s -> s.startsWith("-javaagent:" + agentJar.getAbsolutePath()));
        if (notExistsMyAgent) {
            String javaAgent = "-javaagent:" + agentJar.getAbsolutePath() + "=";
            if (project != null) {
                MySpaceSettings.State state = MySpaceSettings.getInstance(project).getState();
                if (state == null) {
                    return;
                }
                // 现在你可以调用你的 Service 了
                Set<String> packages = state.packageNames;
                if (packages.isEmpty()) {
                    return;
                }
                javaAgent = javaAgent + String.join(";", packages);
                vmParametersList.add(javaAgent);
            }
        }
        RunExecutionListener.open();
    }
}
