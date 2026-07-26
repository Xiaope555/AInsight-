package com.ainsight.agent.core;

/** 一次工具执行的结果:内容 + 成败 + 耗时 */
public record ToolExecutionResult(String result, boolean success, long costMs) {
}
