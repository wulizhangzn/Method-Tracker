package com.znzhang.timetracker.vo;


import com.google.gson.Gson;

public class RawData {
    public String signature;
    public long duration; // 耗时
    // 辅助字段，记录当前节点的深度
    public int depth;
    public long startTime;

    public RawData(int depth, String signature, long duration, long startTime) {
        this.depth = depth;
        this.signature = signature;
        this.duration = duration;
        this.startTime = startTime;
    }

    @Override
    public String toString() {
        return  new Gson().toJson(this);
    }

    public long getStartTime() {
        return startTime;
    }

    public int getDepth() {
            return depth;
    }
}