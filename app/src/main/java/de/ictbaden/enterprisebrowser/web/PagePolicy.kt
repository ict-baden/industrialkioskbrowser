package de.ictbaden.enterprisebrowser.web

import de.ictbaden.enterprisebrowser.config.ScanTerminator

/**
 * Scanner behaviour requested by the current page through meta tags, e.g.
 * `<meta http-equiv="scanner" content="Disable">` or
 * `<meta http-equiv="ScannerNavigate" content="javascript:lookup(%s)">`.
 */
data class PagePolicy(
    val scannerEnabled: Boolean? = null,
    val terminator: ScanTerminator? = null,
    val navigateTemplate: String? = null,
) {
    companion object {
        val NONE = PagePolicy()

        fun fromMetaTags(tags: List<Pair<String, String>>): PagePolicy {
            var policy = NONE
            for ((rawName, rawContent) in tags) {
                val content = rawContent.trim()
                when (rawName.trim().lowercase()) {
                    "scanner" -> content.split(',', ';').map { it.trim().lowercase() }.forEach { value ->
                        policy = when (value) {
                            "enable", "resume" -> policy.copy(scannerEnabled = true)
                            "disable", "suspend" -> policy.copy(scannerEnabled = false)
                            "autotab" -> policy.copy(terminator = ScanTerminator.TAB)
                            "autoenter" -> policy.copy(terminator = ScanTerminator.ENTER)
                            "autotabandenter" -> policy.copy(terminator = ScanTerminator.TAB_ENTER)
                            "autoenterandtab" -> policy.copy(terminator = ScanTerminator.ENTER_TAB)
                            "noauto" -> policy.copy(terminator = ScanTerminator.NONE)
                            else -> policy
                        }
                    }
                    "scannerterminator" -> policy = policy.copy(terminator = ScanTerminator.parse(content, ScanTerminator.NONE))
                    "scannernavigate" -> if (content.isNotEmpty()) policy = policy.copy(navigateTemplate = content)
                }
            }
            return policy
        }
    }
}
