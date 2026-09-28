package com.situ.aichat.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.situ.aichat.diagnostics.LogSource

/**
 * 上下文日志条目（批 D·移植 iOS `LogEntry`）：记录每一次和大模型的调用。
 *
 * 成功 → 完整上下文 + 回复内容；失败 → 完整上下文 + 错误原因。token 区：API 返回精确值优先、否则 TokenEstimator
 * 估算（[isTokenEstimated] 据此置位）。[contextSegmentsJson] = `[ContextSegment]` 的 JSON（仅聊天管线非空）。
 *
 * 隐私铁规：
 * - **绝不存 API key**——[fullContext] 仅由消息渲染、不含鉴权头。
 * - `logDetailEnabled=false`（默认）时 [fullContext]/[responseContent] 存空，只留元数据 + 分段统计。
 * - 容量自动轮转（[com.situ.aichat.diagnostics.LogRetention]）；**本表不进备份导出**（敏感上下文不跨设备）。
 *
 * iOS 用 `durationSeconds: Double`；安卓统一存毫秒 [durationMillis]，展示层换算。iOS 的 `@Attribute(.externalStorage)`
 * 无对应物——Room 大 TEXT 列即可。容量三道闸 = detail 默认关 + 轮转 + 极端安全帽
 * （[com.situ.aichat.diagnostics.LogContextFormat.STORED_TEXT_HARD_LIMIT]·2026-07-16 全量记录：
 * 不再按重任务剪 1200/8000/6000 字）；列表读取走轻投影 [com.situ.aichat.diagnostics.LogListRow] 不抱正文列。
 */
@Entity(tableName = "log_entries", indices = [Index(value = ["timestampMillis"])])
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long = System.currentTimeMillis(),
    val characterName: String = "",
    val modelName: String = "",
    val isSuccess: Boolean = true,
    val source: String = LogSource.CHAT,
    val messageCount: Int = 0,
    val durationMillis: Long? = null,
    val errorMessage: String? = null,
    val fullContext: String = "",
    val responseContent: String? = null,
    val contextSegmentsJson: String = "",
    /** 工具调用遥测 [com.situ.aichat.diagnostics.LogToolInfo] 的 JSON（仅聊天管线非空；空=旧行/无遥测→详情页隐藏该节）。 */
    @ColumnInfo(defaultValue = "''") val toolInfoJson: String = "",
    // token 用量（精确值来自 API usage，估算值来自 TokenEstimator）
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val reasoningTokens: Int = 0,
    val cacheHitTokens: Int = 0,
    val cacheMissTokens: Int = 0,
    val isTokenEstimated: Boolean = true,
    // —— 时间感知四期·图纸三（v51）：对话 / 轮次关联 + 实际发送 + 请求形状 + 失败分类 ——
    /** 所属会话 uuid（聊天 / 语音回合、回合带出的后台调用、补回复、余温、图片理解）；其余后台任务 = null。 */
    val conversationUuid: String? = null,
    /** 角色 uuid（有 [com.situ.aichat.diagnostics.LogTrace] 时记）；其余 null，照旧按 [characterName] 归组。 */
    val characterUuid: String? = null,
    /** 轮次 id：同一轮的主调用与它带出的后台调用共用；非回合调用 = null。 */
    val turnId: String? = null,
    /** 锚点消息 uuid：聊天回合 = 本轮第一条用户消息；图片理解 = 那条图片消息；其余 null。 */
    val anchorMessageUuid: String? = null,
    /** 服务商类型（[com.situ.aichat.data.model.ApiProviderType.raw]）；没捕获到请求 = null。 */
    val providerType: String? = null,
    /** 发送前改写计数（[com.situ.aichat.diagnostics.LogSendAdaptation] 的 JSON）；'' = 没捕获到请求。 */
    @ColumnInfo(defaultValue = "''") val sendAdaptationJson: String = "",
    /** 实际发出的请求体（媒体换替身·不含 key）；仅「记录完整详细内容」开着时存，否则 ''。 */
    @ColumnInfo(defaultValue = "''") val requestJson: String = "",
    /** 请求形状（[com.situ.aichat.diagnostics.LogRequestShape] 的 JSON：逐条指纹 / 角色 / 估算 token / 时间标记）；恒存，不含正文。 */
    @ColumnInfo(defaultValue = "''") val shapeJson: String = "",
    /** 失败分类（[com.situ.aichat.diagnostics.LlmFailureKind.raw]）；成功 = null；v50 老失败行 = null（读时按 [errorMessage] 现算）。 */
    val failureKind: String? = null,
    /** HTTP 状态码（能取到时）。 */
    val httpStatus: Int? = null,
)
