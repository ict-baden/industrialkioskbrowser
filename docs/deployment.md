# Verteilung

Wie App und Konfiguration auf die Geräte kommen. Die Konfiguration ist immer dieselbe
JSON-Datei ([Referenz](configuration.md), [Vorlagen](../examples)) – nur der Transportweg
unterscheidet sich.

| Weg | Geeignet für | Konfiguration |
|---|---|---|
| [Managed Configuration](#managed-configuration-alle-emms) | SOTI, Intune, Workspace ONE, … (Android Enterprise) | Schlüssel `config_json` |
| [Konfigurationsdatei](#konfigurationsdatei) | SOTI File Sync, Zebra StageNow/MX, adb | `enterprisebrowser.json` |
| [Broadcast-Intent](#broadcast-intent-stagenow-adb) | Zebra StageNow (Intent CSP), Skripte, adb | Extra `config` |
| [QR-Code](#qr-code) | Einzelgeräte, Tests | JSON im QR-Code |

Paketname: **`io.github.pieewiee.enterprisebrowser`**

## Managed Configuration (alle EMMs)

Die App bringt ein Schema mit, das jedes EMM mit Android-Enterprise-Unterstützung anzeigt:

| Schlüssel | Typ | Wirkung |
|---|---|---|
| `config_json` | String | **Komplette Konfiguration** (Inhalt einer JSON-Datei aus `examples/`). Hat Vorrang. |
| `start_url` | String | Einfacher Modus: legt ein Profil „managed“ an, das beim Start öffnet |
| `profile_name` | String | Name dieses Profils |
| `kiosk_mode` | Bool | Toolbar und Systemleisten aus, Beenden verborgen |
| `admin_password` | String | Setzt das Admin-Passwort |

Die Werte werden beim App-Start und bei jeder Rückkehr zur Profilliste übernommen,
sobald sie sich geändert haben. Fehler (z. B. ungültiges JSON) stehen in der App unter
**Einstellungen → Letzter Provisionierungsfehler**.

## SOTI MobiControl

Bezeichnungen der Menüs unterscheiden sich je nach MobiControl-Version und
Geräteverwaltung (Android Enterprise oder Android+). Der Ablauf:

1. **App hochladen** – APK aus den [Releases](../../../releases/latest) als Enterprise-App
   in den App-Katalog (bzw. als Paket) aufnehmen und den Geräten zuweisen.
   Ab Version 0.2.0 enthält die APK PNG-Icons, damit MobiControl das App-Icon anzeigt.
2. **Konfiguration – Variante A, Managed Configuration** (Android Enterprise):
   in den App-Einstellungen der Enterprise-App die App-Konfiguration öffnen und unter
   `config_json` den Inhalt der JSON-Datei einfügen.
3. **Konfiguration – Variante B, Datei** (z. B. Android+ mit Zebra-Agent):
   per File Sync bzw. Paket `enterprisebrowser.json` nach `/enterprise/usr/` auf dem
   Gerät legen (Zebra, bleibt auch nach einem Enterprise Reset erhalten).
4. **Kiosk** – in einem Lockdown-/Kiosk-Profil den EnterpriseBrowser als einzige App
   freigeben. Unter Android Enterprise (Dedicated Device) startet die App dann
   automatisch im Lock-Task-Modus. Zusätzlich in der Konfiguration `global.hideExit`
   setzen ([Vorlage](../examples/kiosk-single-app.json)).
5. **Prüfen** – App auf einem Gerät öffnen: Die Profile aus der Konfiguration
   erscheinen auf der Startseite, ein Profil mit `launchOnStartup` öffnet sofort.

## Microsoft Intune

1. Die App muss für Android-Enterprise-Geräte über **Managed Google Play** (private App)
   bereitgestellt werden. Google Play nimmt nur release-signierte APKs an – die aktuelle
   Debug-APK eignet sich dafür noch nicht (Release-Signierung ist in Vorbereitung).
2. **App-Konfigurationsrichtlinie** (verwaltete Geräte) anlegen, Konfigurationsdesigner
   verwenden und `config_json` bzw. `start_url` usw. setzen.
3. Für Kiosk: Gerät als *Dedicated Device* einrichten und die App im Kioskprofil als
   einzige App erlauben.

## VMware Workspace ONE / andere EMMs

App als interne App bzw. über Managed Google Play hinzufügen und in der
**App-Konfiguration** die Schlüssel aus der Tabelle oben setzen. Jedes EMM, das
Android Managed Configurations unterstützt, zeigt das Schema automatisch an.

## Zebra StageNow / MX

- **App installieren:** AppMgr CSP mit der APK.
- **Konfiguration als Datei:** FileMgr CSP, Ziel `/enterprise/usr/enterprisebrowser.json`.
- **Konfiguration per Intent:** Intent CSP, *Send Broadcast* mit
  Action `io.github.pieewiee.enterprisebrowser.action.INSTALL_CONFIG`,
  Package `io.github.pieewiee.enterprisebrowser` und String-Extra `config` (JSON).
  Ist bereits ein Admin-Passwort gesetzt, zusätzlich String-Extra `admin_password`.
- **Scanner:** Die App legt beim ersten Start ein DataWedge-Profil „EnterpriseBrowser“
  an. Decoder (Symbologien, Längen, Beleuchtung …) können danach per DataWedge-CSP oder
  in der DataWedge-App angepasst werden – die App überschreibt sie nicht.

## Konfigurationsdatei

Die App liest `enterprisebrowser.json` aus

1. `Android/data/io.github.pieewiee.enterprisebrowser/files/` (App-Ordner; existiert
   nach dem ersten Start der App)
2. `/enterprise/usr/` (Zebra, persistent)

Eine Datei wird übernommen, sobald sich Zeitstempel oder Größe ändern – beim App-Start
oder bei Rückkehr zur Profilliste.

```bash
adb push examples/sap-its-mobile.json \
  /sdcard/Android/data/io.github.pieewiee.enterprisebrowser/files/enterprisebrowser.json
```

## Broadcast-Intent (StageNow, adb)

```bash
adb shell am broadcast \
  -a io.github.pieewiee.enterprisebrowser.action.INSTALL_CONFIG \
  -n io.github.pieewiee.enterprisebrowser/.provisioning.ConfigReceiver \
  --es config '{"profiles":[{"name":"WMS","startUrl":"https://wms.example.com"}]}'
```

Antwort (bei geordnetem Broadcast) im Result: `-1` = installiert, `2` = Admin-Passwort
fehlt/falsch, `3` = ungültige Konfiguration.

## QR-Code

In der App **Einstellungen → Per Barcode importieren** wählen und einen QR-Code
scannen, der die JSON-Konfiguration enthält. Ein QR-Code fasst knapp 3 KB –
für größere Konfigurationen (Ressourcen, Logo) Managed Configuration oder Datei nutzen.

```bash
qrencode -r examples/minimal.json -o config-qr.png
```

## Updates und Signatur

Android installiert ein Update nur, wenn die neue APK mit demselben Schlüssel signiert
ist wie die installierte. Die aktuellen Releases sind **debug-signiert**. Beim späteren
Wechsel auf einen Release-Schlüssel muss die App einmalig deinstalliert und neu
installiert werden (Konfiguration danach erneut verteilen).
