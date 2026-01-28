package com.znzhang.methodtracker;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.content.ContentManagerListener;
import com.intellij.util.messages.MessageBusConnection;
import com.znzhang.methodtracker.action.ShowFlameGraphSettingsAction;
import com.znzhang.methodtracker.context.AgentDataContext;
import com.znzhang.methodtracker.service.AgentCommunicationService;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.Set;


public class MyToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        showWelcomePage(project, toolWindow);
        MessageBusConnection connect = project.getMessageBus().connect();
        connect.subscribe(AgentCommunicationService.DATA_TOPIC, (AgentCommunicationService.AgentDataListener) AgentDataContext::addAgentData);
        // 1. 创建你的设置 Action
        ShowFlameGraphSettingsAction settingsAction = new ShowFlameGraphSettingsAction();

        // 2. 将 Action 添加到标题栏
        // 注意：这里接受的是 List<? extends AnAction>
        toolWindow.setTitleActions(List.of(settingsAction));
        // 加载之前的标签
        loadLastTab(project,toolWindow);
    }

    private void loadLastTab(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        DumbService.getInstance(project).runWhenSmart(() -> {
            MySpaceSettings.State state = MySpaceSettings.getInstance(project).getState();
            if (state == null) {
                return;
            }
            Set<String> monitoredMethods = state.monitoredMethods;
            if (monitoredMethods.isEmpty()) {
                return;
            }
            for (String monitoredMethod : monitoredMethods) {
                new FlameGraphTabManager(toolWindow, project).addMethodTab(monitoredMethod);
            }
        });
    }

    private void showWelcomePage(@NotNull Project project, ToolWindow toolWindow) {
        toolWindow.setToHideOnEmptyContent(false);
        JBLabel descLabel = new JBLabel("Right-click a method in the editor to start.", SwingConstants.CENTER);
        Content emptyContent = toolWindow.getContentManager().getFactory().createContent(descLabel, "", false);
        emptyContent.setCloseable(false);
        toolWindow.getContentManager().addContent(emptyContent);
        toolWindow.getContentManager().addContentManagerListener(new ContentManagerListener() {
            @Override
            public void contentRemoveQuery(@NotNull ContentManagerEvent event) {
                Content content = event.getContent();
                String displayName = event.getContent().getDisplayName();
                MySpaceSettings.getInstance(project).removeMethod(displayName, project);
                // 当所有页签都被关闭时
                if (toolWindow.getContentManager().getContentCount() == 1) {
                    JBLabel descLabel = new JBLabel("Right-click a method in the editor to start.", SwingConstants.CENTER);
                    content.setComponent(descLabel); // 换回 Label
                    content.setDisplayName("");      // 清空页签标题
                    content.setCloseable(false);     // 占位状态不可关闭
                    event.consume();
                }
            }
        });
    }
}
