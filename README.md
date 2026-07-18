# FALCO Android

FALCO (Fast Audio Library Catalog Organizer) è un catalogatore musicale Android nativo, scritto in Kotlin e Jetpack Compose. Funziona localmente: i file e il database non dipendono da Lovable o da un servizio cloud.

## Funzioni

- scelta di più cartelle con Storage Access Framework e accesso persistente;
- scansione ricorsiva MP3, FLAC, WAV, M4A, AAC, OGG e AIFF;
- catalogo Room con metadati tecnici e musicali;
- ricerca istantanea, filtri combinabili e cinque ordinamenti;
- tag personali, note, rating, stato e preferiti;
- riproduzione ExoPlayer con play, pausa e seek;
- analisi dei probabili duplicati;
- dashboard con brani, durata, spazio, artisti, generi e duplicati;
- tema scuro e UI Compose adattiva.

## Build locale

Servono JDK 17 e Android SDK 35.

```bash
gradle :app:assembleDebug
```

L'APK sarà in `app/build/outputs/apk/debug/app-debug.apk`.

## APK da GitHub Actions

Ogni pull request e push su `main` esegue test, lint e build. Apri la relativa esecuzione nella scheda **Actions**, quindi scarica l'artifact **falco-debug-apk** dalla sezione Artifacts.

## Privacy

FALCO accede esclusivamente alle cartelle scelte dall'utente. Catalogo, annotazioni e preferenze rimangono nel database locale dell'app.
