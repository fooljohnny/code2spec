package io.github.code2spec;

/**
 * Reports progress during parsing and LLM enhancement.
 * Null-safe: all methods no-op when reporter is null.
 */
public class ProgressReporter {
    private final long startTime = System.currentTimeMillis();
    private long promptTokens;
    private long completionTokens;
    private int llmRequestCount;
    private final boolean verbose;

    public ProgressReporter() {
        this(false);
    }

    public ProgressReporter(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * 在 -v 模式下打印某步骤耗时。
     */
    public void verboseTiming(String step, long ms) {
        if (verbose) {
            System.out.println("        [耗时] " + step + ": " + ms + " ms");
        }
    }

    public void onParseJavaStart(int totalFiles) {
        System.out.println("[1/4] 解析 Java 源码: " + totalFiles + " 个文件");
    }

    public void onParseJavaFile(int current, int total, String fileName) {
        if (total > 0) {
            String name = fileName != null && fileName.contains("/") ? fileName.substring(fileName.lastIndexOf('/') + 1) : fileName;
            System.out.println("      解析 Java: " + current + "/" + total + " " + name);
        }
    }

    public void onParseOpenApiStart(int totalFiles) {
        System.out.println("[2/4] 解析 OpenAPI/Swagger: " + totalFiles + " 个文件");
    }

    public void onParseOpenApiFile(int current, int total, String fileName) {
        if (total > 0) {
            String name = fileName != null && fileName.contains("/") ? fileName.substring(fileName.lastIndexOf('/') + 1) : fileName;
            System.out.println("      解析 OpenAPI: " + current + "/" + total + " " + name);
        }
    }

    public void onMergeAndExport() {
        System.out.println("[3/4] 合并结果并导出");
    }

    public void onLlmEndpointStart(int current, int total, String uri) {
        if (total > 0) {
            System.out.println("      LLM 增强端点: " + current + "/" + total + " " + uri);
        } else {
            System.out.println("      LLM 增强端点: " + current + " " + uri);
        }
    }

    public void onLlmErrorCodeStart(int current, int total, String code) {
        if (total > 0) {
            System.out.println("      LLM 增强错误码: " + current + "/" + total + " " + code);
        } else {
            System.out.println("      LLM 增强错误码: " + current + " " + code);
        }
    }

    public void addTokens(int prompt, int completion) {
        this.promptTokens += prompt;
        this.completionTokens += completion;
        this.llmRequestCount++;
    }

    /**
     * 记录并打印每次 LLM 调用统计（调用序号、目标、token 消耗）。
     */
    public synchronized void reportLlmCall(String type, String target, int promptTokens, int completionTokens) {
        this.promptTokens += promptTokens;
        this.completionTokens += completionTokens;
        this.llmRequestCount++;
        System.out.println("      [LLM 调用 " + llmRequestCount + "] " + type + " " + target
                + "  prompt=" + promptTokens + " completion=" + completionTokens + " total=" + (promptTokens + completionTokens));
    }

    private static final int VERBOSE_INPUT_MAX = 1200;
    private static final int VERBOSE_OUTPUT_MAX = 600;

    /**
     * -v 模式下打印 LLM 调用详情：URI、输入、输出、耗时、token 消耗。
     */
    public void verboseLlmDetail(String uri, String input, String output, long durationMs, int promptTokens, int completionTokens) {
        if (!verbose) return;
        System.out.println("        [LLM 调用详情]");
        System.out.println("        URI: " + uri);
        String in = input != null ? input : "";
        System.out.println("        输入: " + truncateForLog(in, VERBOSE_INPUT_MAX) + (in.length() > VERBOSE_INPUT_MAX ? " ... (共 " + in.length() + " 字)" : ""));
        String out = output != null ? output : "";
        System.out.println("        输出: " + truncateForLog(out, VERBOSE_OUTPUT_MAX) + (out.length() > VERBOSE_OUTPUT_MAX ? " ... (共 " + out.length() + " 字)" : ""));
        System.out.println("        耗时: " + durationMs + " ms");
        System.out.println("        Token: prompt=" + promptTokens + " completion=" + completionTokens + " total=" + (promptTokens + completionTokens));
    }

    private static String truncateForLog(String s, int max) {
        if (s == null) return "";
        s = s.replace("\r\n", "\n").replace("\r", "\n");
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /**
     * 打印 LLM 调用失败信息（无论是否 -v 都会显示）。
     */
    public void reportLlmFailure(String type, String target, String uri, String input, long durationMs, Exception e) {
        System.err.println("      [LLM 调用失败] " + type + " " + target);
        System.err.println("        URI: " + (uri != null ? uri : ""));
        String in = input != null ? input : "";
        System.err.println("        输入: " + truncateForLog(in, 400) + (in.length() > 400 ? " ... (共 " + in.length() + " 字)" : ""));
        System.err.println("        失败: " + e.getClass().getSimpleName() + ": " + (e.getMessage() != null ? e.getMessage() : ""));
        System.err.println("        耗时: " + durationMs + " ms");
        if (verbose && e != null) {
            e.printStackTrace(System.err);
        }
    }

    public long getPromptTokens() { return promptTokens; }
    public long getCompletionTokens() { return completionTokens; }
    public long getTotalTokens() { return promptTokens + completionTokens; }
    public int getLlmRequestCount() { return llmRequestCount; }

    public long elapsedMs() {
        return System.currentTimeMillis() - startTime;
    }

    public void printSummary() {
        long elapsed = elapsedMs();
        System.out.println("[4/4] 完成");
        System.out.println();
        System.out.println("--- 统计 ---");
        System.out.println("总耗时: " + formatDuration(elapsed));
        if (llmRequestCount > 0) {
            System.out.println("LLM 请求: " + llmRequestCount + " 次");
            System.out.println("Token 消耗: prompt=" + promptTokens + ", completion=" + completionTokens + ", total=" + getTotalTokens());
        }
    }

    private static String formatDuration(long ms) {
        if (ms < 1000) return ms + " ms";
        if (ms < 60_000) return String.format("%.1f s", ms / 1000.0);
        long sec = ms / 1000;
        return (sec / 60) + " m " + (sec % 60) + " s";
    }
}
