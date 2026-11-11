package com.chethan616.clearpdf.engine

enum class RecentOrder(val label: String) {
    Manual("手动排列"), Opened("最近打开"), Name("文件名称"), Type("文件类型"),
}

fun List<RecentFile>.arranged(order: RecentOrder): List<RecentFile> {
    val withinGroup = when (order) {
        RecentOrder.Manual -> compareBy<RecentFile> { it.position ?: -1 }.thenByDescending { it.opened }
        RecentOrder.Opened -> compareByDescending { it.opened }
        RecentOrder.Name -> compareBy(String.CASE_INSENSITIVE_ORDER) { it: RecentFile -> it.name }.thenByDescending { it.opened }
        RecentOrder.Type -> compareBy<RecentFile> { it.name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    }
    return sortedWith(compareByDescending<RecentFile> { it.pinned }.then(withinGroup))
}
