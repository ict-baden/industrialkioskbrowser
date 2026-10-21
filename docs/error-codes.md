[← README](../README.md)

# Error codes

Every error message shows a code next to the translated text, so a message can be identified in
any of the five languages. Source: [`ErrorCodes.kt`](../app/src/main/java/de/ictbaden/pforte/web/ErrorCodes.kt).

| Code | Message (English) | Typical cause |
|---|---|---|
| `PFT-0001` | Server not found | DNS name unknown, no Wi-Fi, wrong start URL |
| `PFT-0002` | No connection | Server down, firewall, wrong port |
| `PFT-0003` | Timeout | Server or network too slow |
| `PFT-0004` | Insecure connection (handshake) | TLS version or cipher not supported |
| `PFT-0005` | Page not available | Other load errors; the technical reason (`net::ERR_…`) is shown in brackets |
| `PFT-0006` | Error *n* | The server answered with HTTP ≥ 400 (only with `errorPages.showHttpErrors`) |
| `PFT-0007` | Insecure connection (certificate) | Certificate untrusted, expired or for another name – install the CA through the MDM |
| `PFT-0010` | Page not allowed | Navigation to a host outside `allowList` |
| `PFT-0011` | Scan target not allowed | `ScannerNavigate` points to a host outside `allowList` |
| `PFT-0012` | Downloads are disabled | `allowDownloads` is off |
| `PFT-0013` | This download type is not supported | Download that is not `http`/`https` (e.g. `blob:`) |
| `PFT-0020` | Profile not found | The profile was removed while it was being opened |
| `PFT-0100` | Last provisioning error | A configuration from the MDM, a file or a broadcast was rejected; the detail names the JSON field |
| `PFT-0101` | Import failed | Invalid configuration in the import file or QR code |
| `PFT-0102` | Could not read the file | Import file not readable |
| `PFT-0103` | Export failed | Target not writable |

Validation details of a configuration (e.g. `global.language: unknown language "tlh"`) stay in
English because they quote JSON field names.
