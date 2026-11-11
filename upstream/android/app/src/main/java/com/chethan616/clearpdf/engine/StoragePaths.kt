package com.chethan616.clearpdf.engine

/** Only map provider metadata when the storage root is actually known. */
internal object StoragePaths {
    fun document(authority: String?, id: String, roots: Map<String, String>): String? = when (authority) {
        "com.android.providers.downloads.documents" -> id.takeIf { it.startsWith("raw:/") }?.removePrefix("raw:")
        "com.android.externalstorage.documents" -> {
            if (':' !in id) null else roots.entries.firstOrNull { it.key.equals(id.substringBefore(':'), ignoreCase = true) }?.value?.let { join(it, id.substringAfter(':')) }
        }
        else -> null
    }
    fun relative(root: String?, directory: String?, name: String?): String? {
        if (root == null || directory == null || name.isNullOrBlank() || '/' in name) return null
        return join(root, directory.trimEnd('/') + if (directory.isEmpty()) name else "/$name")
    }
    fun externalProvider(root: String, configuredPath: String, segments: List<String>): String? {
        val prefix = configuredPath.split('/').filter { it.isNotEmpty() && it != "." }
        return join(root, (prefix + segments).joinToString("/"))
    }
    private fun join(root: String, relative: String): String? {
        if (!root.startsWith('/') || relative.startsWith('/') || relative.split('/').any { it == ".." || it == "." }) return null
        return root.trimEnd('/') + if (relative.isEmpty()) "" else "/$relative"
    }
}
