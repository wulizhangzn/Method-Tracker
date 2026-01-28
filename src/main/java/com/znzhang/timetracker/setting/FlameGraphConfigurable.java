package com.znzhang.timetracker.setting;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.FormBuilder;
import com.znzhang.timetracker.MySpaceSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;

public class FlameGraphConfigurable implements SearchableConfigurable {
    private final Project project;
    private final MySpaceSettings settings;
    private CollectionListModel<String> listModel;

    public FlameGraphConfigurable(Project project) {
        this.project = project;
        this.settings = MySpaceSettings.getInstance(project);
    }

    @Override
    public @NotNull String getId() {
        return "com.znzhang.timetracker.settings";
    }

    @Override
    public @NlsContexts.ConfigurableName String getDisplayName() {
        return "Method Tracker";
    }

    @Override
    public @Nullable JComponent createComponent() {
        // 1. 初始化列表模型
        if (settings.getState() == null) {
            listModel = new CollectionListModel<>();
        } else {
            listModel = new CollectionListModel<>(settings.getState().packageNames);
        }
        JBList<String> packageList = new JBList<>(listModel);

        // 2. 使用 ToolbarDecorator 创建带 [+][-] 的原生列表面板
        JPanel tablePanel = ToolbarDecorator.createDecorator(packageList)
                .setAddAction(button -> {
                    String newPkg = Messages.showInputDialog(project, "Enter package name (e.g. com.myapp):",
                            "Add Monitor Package", null);
                    if (newPkg != null && !newPkg.trim().isEmpty()) {
                        listModel.add(newPkg.trim());
                    }
                })
                .disableUpDownActions()
                .createPanel();

        // 3. 构建整体布局
        return FormBuilder.createFormBuilder()
                .addLabeledComponent("Monitored packages:", tablePanel, 10, true)
                .addVerticalGap(10)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    @Override
    public boolean isModified() {
        // 判断列表内容是否与 Service 中存储的一致
        return settings.isModifiedPackage(listModel.getItems());
    }

    @Override
    public void apply() {
        // 保存到 PersistentStateComponent
        settings.addPackageName(new ArrayList<>(listModel.getItems()));
    }

    @Override
    public void reset() {
        // 还原 UI 到保存前的状态
        MySpaceSettings.State state = settings.getState();
        if (state != null) {
            listModel.replaceAll(new ArrayList<>(state.packageNames));
        } else {
            listModel.removeAll();
        }
    }
}