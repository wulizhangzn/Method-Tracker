package com.znzhang.methodtracker.vo;

import java.util.ArrayList;
import java.util.List;

public class FlameGraphNode {
    public String name;
    public long value; // 耗时
    public List<FlameGraphNode> children = new ArrayList<>();
    // 辅助字段，记录当前节点的深度
    public transient int depth;
    public transient long startTime;

    public FlameGraphNode(RawData rawData) {
        this.name = rawData.signature;
        this.value = rawData.duration;
        this.depth = rawData.depth;
        this.startTime = rawData.startTime;
    }
}