package de.ictbaden.pforte.web

/**
 * Error codes shown next to translated error messages, so support can identify a message in any
 * language (richtlinien/13-sprachen.md). Listed in docs/error-codes.md – never reuse a number.
 */
object ErrorCodes {
    const val HOST_NOT_FOUND = "PFT-0001"
    const val NO_CONNECTION = "PFT-0002"
    const val TIMEOUT = "PFT-0003"
    const val SSL_HANDSHAKE = "PFT-0004"
    const val PAGE_NOT_AVAILABLE = "PFT-0005"
    const val HTTP_ERROR = "PFT-0006"
    const val CERTIFICATE = "PFT-0007"
    const val PAGE_NOT_ALLOWED = "PFT-0010"
    const val SCAN_TARGET_NOT_ALLOWED = "PFT-0011"
    const val DOWNLOADS_DISABLED = "PFT-0012"
    const val DOWNLOAD_UNSUPPORTED = "PFT-0013"
    const val PROFILE_NOT_FOUND = "PFT-0020"
    const val PROVISIONING = "PFT-0100"
    const val IMPORT_INVALID = "PFT-0101"
    const val FILE_UNREADABLE = "PFT-0102"
    const val EXPORT_FAILED = "PFT-0103"
}
