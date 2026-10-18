package de.ictbaden.enterprisebrowser.web

/**
 * Error codes shown next to translated error messages, so support can identify a message in any
 * language (richtlinien/13-sprachen.md). Listed in docs/error-codes.md – never reuse a number.
 */
object ErrorCodes {
    const val HOST_NOT_FOUND = "EB-0001"
    const val NO_CONNECTION = "EB-0002"
    const val TIMEOUT = "EB-0003"
    const val SSL_HANDSHAKE = "EB-0004"
    const val PAGE_NOT_AVAILABLE = "EB-0005"
    const val HTTP_ERROR = "EB-0006"
    const val CERTIFICATE = "EB-0007"
    const val PAGE_NOT_ALLOWED = "EB-0010"
    const val SCAN_TARGET_NOT_ALLOWED = "EB-0011"
    const val DOWNLOADS_DISABLED = "EB-0012"
    const val DOWNLOAD_UNSUPPORTED = "EB-0013"
    const val PROFILE_NOT_FOUND = "EB-0020"
    const val PROVISIONING = "EB-0100"
    const val IMPORT_INVALID = "EB-0101"
    const val FILE_UNREADABLE = "EB-0102"
    const val EXPORT_FAILED = "EB-0103"
}
