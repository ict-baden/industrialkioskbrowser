[← README](../README.md)

# Error codes

Every error message shows a code next to the translated text, so a message can be identified in
any of the five languages. Source: [`ErrorCodes.kt`](../app/src/main/java/de/ictbaden/enterprisebrowser/web/ErrorCodes.kt).

| Code | Message (English) | Typical cause |
|---|---|---|
| `EB-0001` | Server not found | DNS name unknown, no Wi-Fi, wrong start URL |
| `EB-0002` | No connection | Server down, firewall, wrong port |
| `EB-0003` | Timeout | Server or network too slow |
| `EB-0004` | Insecure connection (handshake) | TLS version or cipher not supported |
| `EB-0005` | Page not available | Other load errors; the technical reason (`net::ERR_…`) is shown in brackets |
| `EB-0006` | Error *n* | The server answered with HTTP ≥ 400 (only with `errorPages.showHttpErrors`) |
| `EB-0007` | Insecure connection (certificate) | Certificate untrusted, expired or for another name – install the CA through the MDM |
| `EB-0010` | Page not allowed | Navigation to a host outside `allowList` |
| `EB-0011` | Scan target not allowed | `ScannerNavigate` points to a host outside `allowList` |
| `EB-0012` | Downloads are disabled | `allowDownloads` is off |
| `EB-0013` | This download type is not supported | Download that is not `http`/`https` (e.g. `blob:`) |
| `EB-0020` | Profile not found | The profile was removed while it was being opened |
| `EB-0100` | Last provisioning error | A configuration from the MDM, a file or a broadcast was rejected; the detail names the JSON field |
| `EB-0101` | Import failed | Invalid configuration in the import file or QR code |
| `EB-0102` | Could not read the file | Import file not readable |
| `EB-0103` | Export failed | Target not writable |

Validation details of a configuration (e.g. `global.language: unknown language "tlh"`) stay in
English because they quote JSON field names.
