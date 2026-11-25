# Rosenweg Launcher

Startbildschirm für die Smartphones der Siedlung Rosenweg in Kaiseraugst. Die Standard-Startseite enthält das Uhr-Widget und Rosenweg Kamera; auf geteilten Telefonen hat jede Person ihre eigene Startseite.

## Im Rosenweg

- **Startseite pro Person:** Auf geteilten Telefonen liegt die Startseite der angemeldeten Person auf der Telefonanlage (über Rosenweg Gerät) und wird nach dem Anmelden neu aufgebaut.
- **Einstellungen pro Person:** Auf geteilten Telefonen gehören die Einstellungen der angemeldeten Person. Sie liegen (über Rosenweg Gerät) auf der Telefonanlage und sind nach dem Anmelden an jedem geteilten Telefon wieder da. Ohne Rosenweg Gerät bleiben sie wie gewohnt auf dem Handy.
- Kein Store-Symbol. Auf verwalteten Rosenweg-Telefonen gibt es weder App-Info noch Deinstallieren; System-Telefon-Apps sind ausgeblendet, sobald Rosenweg Telefon installiert ist, und Rosenweg Gerät kann weitere Apps ausblenden.
- **Rosenweg Updater:** Auf Handys ohne Rosenweg Gerät prüft die App den App-Katalog der Telefonanlage und bietet Aktualisierungen für alle installierten Rosenweg-Apps an. Jede APK wird vorher geprüft (Prüfsumme, Paketname, Version, Rosenweg-Signatur); Android lässt jede Installation bestätigen. Auf geteilten Telefonen übernimmt das Rosenweg Gerät.

## Installation

- **Geteilte Rosenweg-Telefone:** Rosenweg Gerät installiert die App automatisch aus dem App-Katalog der Telefonanlage und hält sie aktuell.
- **Eigenes Handy:** APK von [www.rosenweg4303.ch/apps](https://www.rosenweg4303.ch/apps) herunterladen und installieren. Danach hält der Rosenweg Updater die Rosenweg-Apps aktuell.

Die App gibt es nicht im Play Store und nicht auf F-Droid. Sie ist mit dem Rosenweg-Schlüssel signiert; Fassungen anderer Herkunft lassen sich nicht darüber installieren.

## Bauen

Die App baut gegen den Rosenweg-Fork der Fossify Commons und die Rosenweg-Module `cloud`/`updater` (`ch.rosenweg4303:*`). Diese kommen aus dem lokalen Maven-Repository (`mavenLocal()`), also zuerst [commons-rosenweg](https://github.com/Rosenweg/commons-rosenweg) veröffentlichen:

```bash
# in commons-rosenweg, Zweig rosenweg
./gradlew -PVERSION=7.1.3-rosenweg.1 publishToMavenLocal

# in diesem Repo, Zweig rosenweg
./gradlew assembleFossRelease
```

Die APK liegt danach unter `app/build/outputs/apk/foss/release/`. Signiert wird mit `keystore.properties` (Vorlage: `keystore.properties_sample`); die Datei und der Schlüssel liegen nicht im Repo. Paketname: `ch.rosenweg4303.home`.

## Herkunft und Lizenz

Fork von [Fossify Launcher](https://github.com/FossifyOrg/Launcher). Der Zweig `main` folgt dem Original, die Rosenweg-Anpassungen liegen im Zweig `rosenweg`. Herzlichen Dank an das Fossify-Team und alle Mitwirkenden für die solide Grundlage.

Lizenz: GNU General Public License v3.0, siehe [LICENSE](LICENSE).
