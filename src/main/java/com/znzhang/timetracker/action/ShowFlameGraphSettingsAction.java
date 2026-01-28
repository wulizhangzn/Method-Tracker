package com.znzhang.timetracker.action;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ShowFlameGraphSettingsAction extends AnAction {
    public ShowFlameGraphSettingsAction() {
        // 使用系统内置的齿轮图标
        super("Method Tracker Settings", "Configure monitor packages", AllIcons.General.Settings);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project != null) {
            // 直接跳转到指定的 Configurable ID
            ShowSettingsUtil.getInstance().showSettingsDialog(project, "com.znzhang.timetracker.settings");
        }
    }
}