package com.znzhang.methodtracker;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.znzhang.methodtracker.service.AgentCommunicationService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// name 决定了 XML 根节点名称，storage 决定了保存的文件名
@Service(Service.Level.PROJECT)
@State(name = "MySpaceSettings", storages = @Storage("MethodTrackerSettings.xml"))
public final class MySpaceSettings implements PersistentStateComponent<MySpaceSettings.State> {

    // 内部类，定义需要保存的数据结构
    public static class State {
        public Set<String> monitoredMethods = new HashSet<>();
        public Set<String> packageNames = new HashSet<>();
    }

    private State myState = new State();

    // 快捷获取实例的方法
    public static MySpaceSettings getInstance(Project project) {
        return project.getService(MySpaceSettings.class);
    }

    @Nullable
    @Override
    public State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.myState = state;
    }

    // 辅助方法：添加方法到列表
    public void addMethod(String methodSignature, Project project, PsiMethod method) {
        myState.monitoredMethods.add(methodSignature);
        myState.packageNames.add(getHeuristicRootPackage(method));
        // 通过 Socket 发送给 Agent
        project.getService(AgentCommunicationService.class).syncMethodsWithAgent("SET_METHODS:");
    }

    // 辅助方法：删除方法
    public void removeMethod(String methodSignature, @NotNull Project project) {
        myState.monitoredMethods.remove(methodSignature);
        // 先不删除，保留在setting中
//        myState.packageNames.removeIf(packageName -> myState.monitoredMethods.stream().noneMatch(s -> s.startsWith(packageName)));
        // 通过 Socket 发送给 Agent
        project.getService(AgentCommunicationService.class).syncMethodsWithAgent("DEL_METHODS:");
    }

    public void addPackageName(List<String> packages) {
        myState.packageNames = new HashSet<>(packages);
    }

    public boolean isModifiedPackage(@NotNull List<String> items) {
        Set<String> temp = new HashSet<>(items);
        return !temp.equals(myState.packageNames);
    }

    private static String getHeuristicRootPackage(PsiMethod method) {
        PsiClass psiClass = method.getContainingClass();
        if (psiClass == null) return "";

        String fqn = psiClass.getQualifiedName(); // 例如 com.example.service.impl.UserService
        if (fqn == null) return "";

        String[] parts = fqn.split("\\.");
        // 如果包名超过3段，通常前3段就是根包 (com.example.app)
        if (parts.length > 3) {
            return parts[0] + "." + parts[1] + "." + parts[2];
        }
        // 否则取父级包
        int lastDot = fqn.lastIndexOf('.');
        return (lastDot > 0) ? fqn.substring(0, lastDot) : fqn;
    }

}