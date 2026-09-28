package com.situ.aichat.diagnostics

/** [com.situ.aichat.data.local.dao.LogDao.previousComparable] 的轻投影（四期·图纸三 §3.10；图纸四加 [timestampMillis]·红框写「和上一轮（HH:mm）相比」用）。 */
data class LogShapeRow(val id: Long, val timestampMillis: Long, val shapeJson: String, val contextSegmentsJson: String)
