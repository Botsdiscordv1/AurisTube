# AurisTube

Puente Android TV / Google TV que ocupa el hueco de "YouTube" en el launcher con
su paquete propio (`com.auristube.bridge`) y **reenvía** cualquier intent dirigido
a "YouTube" hacia
[TizenTube Cobalt](https://github.com/reisxd/TizenTubeCobalt).

## Qué hace

- Se instala con su propio paquete `com.auristube.bridge` (mosaico en el launcher,
  búsqueda por voz, links de YouTube). No necesita root ni tocar la app oficial.
- Reenvía links de YouTube (`youtube.com`, `youtu.be`, `youtube://`,
  `vnd.youtube:`) y acciones de búsqueda/reproducción a TizenTube Cobalt.
- Si Cobalt no acepta el intent, abre su pantalla principal (leanback/main).
- **Instalador integrado:** si TizenTube Cobalt no está instalado, al pulsar el
  mosaico de YouTube se abre una pantalla de setup que descarga el último APK de
  TizenTube Cobalt desde GitHub y lanza el instalador de Android. Así basta con
  instalar este único APK.
- No tiene login, ni analíticas. Solo usa red para descargar Cobalt.

## Qué NO hace

- No crea ni refresca canales de recomendaciones ni entradas **Watch Next /
  "Continuar viendo"** propias. Para eso habría que reconstruir Cobalt, porque
  Android aísla las dos apps y el APK repackeado no puede registrar un puente
  JavaScript nuevo.
- No ocupa el ID de paquete del YouTube TV oficial, así que el botón físico
  "YouTube" del mando (que apunta a esa app) puede no abrir este puente.

## Requisitos

- Android TV o Google TV (minSdk 21).
- [TizenTube Cobalt](https://github.com/reisxd/TizenTubeCobalt) instalado con el
  paquete `io.gh.reisxd.tizentube.cobalt`.
- Opcional: desinstalar el YouTube TV oficial para que este puente sea el que
  maneje los links de YouTube (no es obligatorio).

## Compilar

```
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

Salidas:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

## Firma (release)

La firma se lee de `key.properties` (en la raíz, **no versionado**):

```
storeFile=keystore/auristube-release.jks
storePassword=<password>
keyAlias=auristube
keyPassword=<password>
```

El keystore vive en `keystore/auristube-release.jks` (también ignorado por git).
Si `key.properties` no existe, el build de release cae a la firma debug.

> Guarda una copia del keystore y su contraseña: sin ellos no podrás publicar
> actualizaciones que instalen encima de la versión firmada.

## Instalar

```
adb install -r app/build/outputs/apk/release/app-release.apk
```

Recuerda desinstalar/desactivar antes el YouTube TV oficial.

Primer uso: pulsa el mosaico de YouTube en el launcher. Si Cobalt no está
instalado, aparece la pantalla de setup para descargarlo e instalarlo. Cuando
Android lo pida, concede "Instalar apps desconocidas" a esta app.

## Permisos

- `INTERNET` — descargar el APK de TizenTube Cobalt.
- `REQUEST_INSTALL_PACKAGES` — lanzar el instalador del APK descargado.

## Nota

Proyecto no afiliado a Google, YouTube ni TizenTube. Basado en el mecanismo del
proyecto [tizentube-bridge](https://github.com/TobiPeterG/tizentube-bridge)
(GPL-3.0). Úsalo bajo tu responsabilidad y respetando los términos de servicio
de YouTube.
