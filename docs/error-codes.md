[← README](../README.md)

# Error codes

Every error message shows a code next to the translated text, so a message can be identified in
any of the five languages. Source: [`ErrorCodes.kt`](../app/src/main/java/de/ictbaden/industrialkioskbrowser/web/ErrorCodes.kt).

| Code | Message (English) | Typical cause |
|---|---|---|
| `IKB-0001` | Server not found | DNS name unknown, no Wi-Fi, wrong start URL |
| `IKB-0002` | No connection | Server down, firewall, wrong port |
| `IKB-0003` | Timeout | Server or network too slow |
| `IKB-0004` | Insecure connection (handshake) | TLS version or cipher not supported |
| `IKB-0005` | Page not available | Other load errors; the technical reason (`net::ERR_…`) is shown in brackets |
| `IKB-0006` | Error *n* | The server answered with HTTP ≥ 400 (only with `errorPages.showHttpErrors`) |
| `IKB-0007` | Insecure connection (certificate) | Certificate untrusted, expired or for another name – install the CA through the MDM |
| `IKB-0010` | Page not allowed | Navigation to a host outside `allowList`, or to a scheme or port its entry does not allow (the message then shows them, e.g. `http://example.com:8080`) |
| `IKB-0007` | Insecure connection (certificate) | Certificate untrusted, expired or for another name – install the CA through the MDM. Shown for the page and for resources on its host; an embedded resource from another host is skipped without this page |
| `IKB-0010` | Page not allowed | Navigation to a host outside `allowList` |
| `IKB-0011` | Scan target not allowed | `ScannerNavigate` points to a host outside `allowList` |
| `IKB-0012` | Downloads are disabled | `allowDownloads` is off |
| `IKB-0013` | This download type is not supported | Download that is not `http`/`https` (e.g. `blob:`) |
| `IKB-0014` | Download not allowed | The file comes from a host outside `allowList`; add the host or serve the file from an allowed one |
| `IKB-0020` | Profile not found | The profile was removed while it was being opened |
| `IKB-0030` | No access to the camera | Camera scanning: the camera permission was denied (Android settings → Apps → Industrial Kiosk Browser → Permissions, or grant it through the MDM) |
| `IKB-0031` | Camera could not be started | Camera scanning: no usable camera, or another app holds it |
| `IKB-0032` | Text recognition is not available | ML Kit could not be started on this device (`scanner.ocr`) |
| `IKB-0033` | NFC is switched off | *Settings → profile → NFC*: the device has NFC but it is off; tap to open the system settings |
| `IKB-0034` | The NFC tag has no data to enter | The tag has no NDEF text or link while `scanner.nfc.read` is `ndef` |
| `IKB-0040` | Scan discarded: too many scans are waiting | More than `scanner.queue.maxPending` scans arrived while the page was not ready (slow server, page busy) |
| `IKB-0041` | Scan discarded: the page was not ready in time | The queue made no progress for `scanner.queue.timeoutMs`: the page did not become ready, no text field got the focus, or the field kept the previous scan (`waitForFieldClear`). Pages that handle the scan event themselves should call `preventDefault()` |
| `IKB-0042` | Scan discarded: the page was left | While scans waited, an error page appeared or a navigation outside `allowList` was blocked |
| `IKB-0043` | Scan discarded: the session was closed | Scans were still waiting when the session ended, e.g. because a new configuration restarted it |
| `IKB-0100` | Last provisioning error | A configuration from the MDM, a file or a broadcast was rejected; the detail names the JSON field |
| `IKB-0101` | Import failed | Invalid configuration in the import file or QR code |
| `IKB-0102` | Could not read the file | Import file not readable |
| `IKB-0103` | Export failed | Target not writable |
| `IKB-0104` | Configuration too large for a barcode | *Export as barcode*: even compressed the configuration does not fit into a QR code (about 2.9 KB), usually because of a logo or other resources. Export it as a file |
| `IKB-0105` | The file is not a certificate | *Settings → Trusted certificates → Import*: the file is not a PEM or DER X.509 certificate (`.crt`, `.cer`, `.pem`) or larger than 256 KB |

Validation details of a configuration (e.g. `global.language: unknown language "tlh"`) stay in
English because they quote JSON field names.
