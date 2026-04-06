package com.android.autopay.data.utils

object SourceFilter {
    fun normalizeSource(value: String): String {
        return value.trim().lowercase()
    }
    fun normalizeSources(values: Set<String>): Set<String> {
        return values
            .map { normalizeSource(it) }
            .filter { it.isNotBlank() }
            .toSet()
    }
    fun isSmsAllowed(sender: String, selectedSenders: Set<String>): Boolean {
        if (selectedSenders.isEmpty()) return true
        val normalizedSender: String = normalizeSource(sender)
        return selectedSenders.any { normalizeSource(it) == normalizedSender }
    }
    fun isBankApplicationAllowed(packageName: String, selectedPackageNames: Set<String>): Boolean {
        if (selectedPackageNames.isEmpty()) return true
        val normalizedPackageName: String = normalizeSource(packageName)
        return selectedPackageNames.any { normalizeSource(it) == normalizedPackageName }
    }
    fun mergeKnownAndObserved(
        selectedSources: Set<String>,
        knownSources: Set<String>,
        observedSources: List<String>
    ): List<String> {
        val mergedSources: List<String> = (selectedSources + knownSources + observedSources)
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val uniqueSources: List<String> = mergedSources.distinctBy { normalizeSource(it) }
        return uniqueSources.sortedWith(
            compareBy<String> { !selectedSources.any { selected -> normalizeSource(selected) == normalizeSource(it) } }
                .thenBy { normalizeSource(it) }
        )
    }
}
