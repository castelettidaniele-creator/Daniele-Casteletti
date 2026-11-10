# Inventario Caste – Android (prima versione sorgente)

App Android nativa in Kotlin con database SQLite locale.

Funzioni incluse: categorie personalizzabili (con Tornio, Stampa 3D, minuteria e materiale elettrico già presenti), articoli con quantità/unità/posizione/scorta minima/costo/note, associazione foto dalla galleria, ricerca, modifica/eliminazione, registro acquisti con aggiornamento quantità, avviso scorte basse, esportazione CSV.

## Generare il file APK
1. Installa Android Studio su un PC.
2. Apri questa cartella come progetto.
3. Attendi la sincronizzazione Gradle (prima volta serve internet).
4. Menu Build > Build Bundle(s) / APK(s) > Build APK(s).
5. APK: app/build/outputs/apk/debug/app-debug.apk

## Limiti di questa prima versione
Questa consegna è il progetto sorgente, non un APK già compilato. I dati sono locali sul telefono. La sincronizzazione cloud con Windows e il backup automatico non sono ancora implementati; per ora è disponibile l'esportazione manuale CSV. Le fotografie vengono associate tramite URI Android; la schermata non mostra ancora anteprime.
