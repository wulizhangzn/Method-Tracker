package com.znzhang.methodtracker;


import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.pom.Navigatable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.content.*;
import com.intellij.ui.jcef.JBCefBrowser;
import com.intellij.ui.jcef.JBCefBrowserBase;
import com.intellij.ui.jcef.JBCefJSQuery;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.znzhang.methodtracker.context.AgentDataContext;
import com.znzhang.methodtracker.service.AgentCommunicationService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class FlameGraphTabManager {
    private final ToolWindow toolWindow;
    private final Project project;


    public FlameGraphTabManager(ToolWindow toolWindow, Project project) {
        this.toolWindow = toolWindow;
        this.project = project;
    }

    public void addMethodTab(String methodName) {
        ContentManager contentManager = toolWindow.getContentManager();
        // 1. 检查是否已经打开过该方法的页签
        Content existingContent = contentManager.findContent(methodName);
        if (existingContent != null) {
            // 如果已存在，直接选中它并更新数据（可选）
            contentManager.setSelectedContent(existingContent);
            // 这里可以添加逻辑：通知 JCEF 更新数据，或者直接 return
            return;
        }

        // 2. 创建 JCEF 浏览器
        JBCefBrowser browser = new JBCefBrowser();
        browser.loadHTML(loadFullHtml());
        JBCefJSQuery jsQuery = JBCefJSQuery.create((JBCefBrowserBase) browser);
        // 把 Query 注入到一个全局变量中（只执行一次）
        browser.getCefBrowser().executeJavaScript(
                "window.ideaQuery = function(data) { " + jsQuery.inject("data") + " };",
                browser.getCefBrowser().getURL(), 0
        );
        // 注册 JS 到 Java 的回调 (处理火焰图点击跳转)
        jsQuery.addHandler((signature) -> {
            ApplicationManager.getApplication().invokeLater(() -> jumpToSource(project, signature));
            return new JBCefJSQuery.Response("Success");
        });
        // 3. 创建 Content 页签
        Content content = contentManager.findContent("");
        if (content != null) {
            content.setComponent(browser.getComponent());
            content.setDisplayName(methodName);
            content.setCloseable(false);
        } else {
            // 参数说明：组件, 显示名称(页签标题), 是否允许固定
            content = ContentFactory.getInstance().createContent(
                    browser.getComponent(),
                    methodName,
                    false
            );
            contentManager.addContent(content);
        }
        // 创建一个绑定到 content 生命周期 的连接
        MessageBusConnection connection = project.getMessageBus().connect(content);
        connection.subscribe(AgentCommunicationService.END_TOPIC, (AgentCommunicationService.AgentEndListener) end -> {
            String[] split = end.split("\\|");
            String endMethodName = split[0];
            if (!endMethodName.equals(methodName)) {
                return;
            }
            String json = AgentDataContext.buildTreeJson(methodName, split[1]);
            // 生成火焰图
            browser.getCefBrowser().executeJavaScript(
                    "updateData(" + json + ")",
                    browser.getCefBrowser().getURL(), 0
            );
        });
        // 设置为可关闭
        content.setCloseable(true);
        content.setIcon(AllIcons.Nodes.Method);
        Disposer.register(content, browser);
        contentManager.setSelectedContent(content);
    }

    private String loadFullHtml() {
        try {
            // 读取 HTML 模板
            try (InputStream is = getClass().getResourceAsStream("/flamegraph/flamegraph.html")) {
                if (is == null) throw new IOException("Resource not found: " + "/flamegraph/flamegraph.html");
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return "<html><body>加载失败: " + e.getMessage() + "</body></html>";
        }
    }

    private void jumpToSource(Project project, String signature) {
        if (!signature.contains("#")) return;
        String className = signature.split("#")[0];
        String methodName = signature.split("#")[1].split("\\(")[0];


        ReadAction.nonBlocking(() -> {
                    // 在后台线程查找类
                    PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(
                            className,
                            GlobalSearchScope.allScope(project)
                    );

                    if (psiClass == null) return null;

                    // 在类中查找方法
                    PsiMethod[] methods = psiClass.findMethodsByName(methodName, false);
                    return methods.length > 0 ? methods[0] : psiClass;
                })
                .inSmartMode(project)
                .finishOnUiThread(ModalityState.defaultModalityState(), targetElement -> {
                    // 2. 只有这里是回到 EDT 执行的：跳转编辑器
                    if (targetElement != null && ((Navigatable) targetElement).canNavigate()) {
                        ((Navigatable) targetElement).navigate(true);
                    }
                }).submit(AppExecutorUtil.getAppExecutorService());
    }
}