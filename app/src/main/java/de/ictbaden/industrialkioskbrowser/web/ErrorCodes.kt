package de.ictbaden.industrialkioskbrowser.web

/**
 * Error codes shown next to translated error messages, so support can identify a message in any
 * language (richtlinien/13-sprachen.md). Listed in docs/error-codes.md – never reuse a number.
 */
object ErrorCodes {
    const val HOST_NOT_FOUND = "IKB-0001"
    const val NO_CONNECTION = "IKB-0002"
    const val TIMEOUT = "IKB-0003"
    const val SSL_HANDSHAKE = "IKB-0004"
    const val PAGE_NOT_AVAILABLE = "IKB-0005"
    const val HTTP_ERROR = "IKB-0006"
    const val CERTIFICATE = "IKB-0007"
    const val PAGE_NOT_ALLOWED = "IKB-0010"
    const val SCAN_TARGET_NOT_ALLOWED = "IKB-0011"
    const val DOWNLOADS_DISABLED = "IKB-0012"
    const val DOWNLOAD_UNSUPPORTED = "IKB-0013"
    const val PROFILE_NOT_FOUND = "IKB-0020"
    const val CAMERA_PERMISSION_DENIED = "IKB-0030"
    const val CAMERA_UNAVAILABLE = "IKB-0031"
    const val PROVISIONING = "IKB-0100"
    const val IMPORT_INVALID = "IKB-0101"
    const val FILE_UNREADABLE = "IKB-0102"
    const val EXPORT_FAILED = "IKB-0103"
}
