package com.znzhang.timetracker.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.znzhang.timetracker.FlameGraphTabManager;
import com.znzhang.timetracker.MySpaceSettings;
import org.jetbrains.annotations.NotNull;


import java.util.StringJoiner;

public class AddMonitorAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        // 获取当前选中的 PSI 元素（类、方法或包）
        PsiElement element = e.getData(CommonDataKeys.PSI_ELEMENT);
        if (!(element instanceof PsiMethod method)) {
            return;
        }
        PsiClass containingClass = method.getContainingClass();
        if (containingClass == null) {
            return;
        }
        String methodSignature = getMethodSignature(method, containingClass);
        // 获取 ToolWindow 并调用添加页签逻辑
        Project project = e.getProject();
        assert project != null;
        ToolWindow tw = ToolWindowManager.getInstance(project).getToolWindow("Method Tracker");
        if (tw != null) {
            new FlameGraphTabManager(tw, project).addMethodTab(methodSignature);
            // 自动打开 ToolWindow 窗口
            tw.show();
            // 保存到配置中
            MySpaceSettings instance = MySpaceSettings.getInstance(project);
            instance.addMethod(methodSignature, project, method);
        }
    }

    private static @NotNull String getMethodSignature(PsiMethod method, PsiClass containingClass) {
        String className = containingClass.getQualifiedName(); // 类全名
        String methodName = method.getName();                // 方法名
        StringJoiner params = new StringJoiner(",");
        for (PsiParameter parameter : method.getParameterList().getParameters()) {
            // 获取参数的擦除类型全名（用于 ByteBuddy 匹配）
            params.add(parameter.getType().getCanonicalText());
        }

        // 4. 组合成一个唯一的标识符，存入配置
        // 格式示例: com.myspace.UserService#save(String,User)
        return className + "#" + methodName + "(" + params + ")";
    }
}