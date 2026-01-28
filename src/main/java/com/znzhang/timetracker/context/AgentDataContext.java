package com.znzhang.timetracker.context;

import com.google.gson.Gson;
import com.znzhang.timetracker.vo.FlameGraphNode;
import com.znzhang.timetracker.vo.RawData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AgentDataContext {

    private static final Map<String, List<RawData>> threadDataMap = new ConcurrentHashMap<>();
    private static final Gson gson = new Gson();

    public static void addAgentData(String data) {
        try {
            String[] parts = data.split("\\|");
            long tid = Long.parseLong(parts[0]);
            int depth = Integer.parseInt(parts[1]);
            String signature = parts[2];
            long duration = Long.parseLong(parts[3]);
            long startTime = Long.parseLong(parts[4]);
            threadDataMap.computeIfAbsent(tid + "", k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(new RawData(depth, signature, duration, startTime));
        } catch (Exception ignored) {
        }
    }

    /**
     * 核心算法：将扁平列表转为 D3 火焰图要求的树形结构
     */
    public static String buildTreeJson(String methodName, String threadId) {
        FlameGraphNode root = null;
        List<RawData> rawData = threadDataMap.get(threadId);
        if (rawData == null || rawData.isEmpty()) {
            return "{}";
        }
        // 1. 确保数据是按时间顺序排列的
        rawData.sort(Comparator.comparing(RawData::getStartTime).thenComparing(RawData::getDepth));
        Stack<FlameGraphNode> stack = new Stack<>();
        for (RawData raw : rawData) {
            FlameGraphNode node = new FlameGraphNode(raw);
            if (root == null && node.name.equals(methodName)) {
                root = node;
                stack.push(root);
                continue;
            } else if (root == null) {
                continue;
            }
            if (node.depth == root.depth) {
                continue;
            }
            FlameGraphNode parent = findParent(stack, node);
            if (parent == null) {
                break;
            }
            // 挂载子节点
            parent.children.add(node);

            // 修正父节点耗时：确保 parent.value >= sum(children.value)
            // 如果 parent 是 Root，我们最后统一计算，如果不是，则实时修正
            if (parent != root) {
                // 简单的修正逻辑：如果子节点耗时总和超过父节点，这里可以根据业务调整
                // 火焰图要求：父节点长度必须覆盖子节点
                if (parent.value < node.value) {
                    parent.value = node.value;
                }
            }

            // 将当前节点入栈，它可能是下一条数据的父节点
            stack.push(node);
        }
        // 递归修正所有节点的 value，确保父节点耗时 >= 子节点耗时总和
        assert root != null;
        calculateTotalValue(root);
        return gson.toJson(root);
    }

    private static FlameGraphNode findParent(Stack<FlameGraphNode> stack, FlameGraphNode node) {
        FlameGraphNode parent = null;
        while (!stack.isEmpty()) {
            FlameGraphNode last = stack.pop();
            if (last.depth < node.depth && last.startTime <= node.startTime) {
                parent = last;
                break;
            }
        }
        if (parent != null) {
            stack.push(parent);
        }
        return parent;
    }

    // 辅助方法：递归计算并修正 Value
    private static long calculateTotalValue(FlameGraphNode node) {
        if (node.children.isEmpty()) return node.value;

        long childrenSum = 0;
        for (FlameGraphNode child : node.children) {
            childrenSum += calculateTotalValue(child);
        }

        // 火焰图逻辑：父节点耗时不能小于子节点耗时之和
        if (node.value < childrenSum) {
            node.value = childrenSum;
        }
        return node.value;
    }

    public static void clearData() {
        threadDataMap.clear();
    }
}
