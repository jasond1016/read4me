package com.read4me.app.audio

/** Duration-weighted largest-remainder allocation. Empty allocations are valid when clips outnumber buckets. */
fun allocateWaveformBuckets(durationsMs: List<Long>, bucketCount: Int): List<Int> {
    require(bucketCount >= 0 && durationsMs.all { it >= 0 })
    if (durationsMs.isEmpty()) return emptyList()
    val total = durationsMs.sum()
    if (total == 0L) return List(durationsMs.size) { 0 }
    val base = durationsMs.map { (it * bucketCount / total).toInt() }.toMutableList()
    val order = durationsMs.indices.sortedWith(
        compareByDescending<Int> { durationsMs[it] * bucketCount % total }.thenBy { it },
    )
    repeat(bucketCount - base.sum()) { base[order[it]]++ }
    return base
}
