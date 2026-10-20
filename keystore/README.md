# Madar signing key

Release builds are signed with `keystore/madar.jks` (git-ignored, never committed: this repo is public).
Every APK installed on the phone so far carries this key, so updates install in place.
Losing it means the next build can't update the installed app (uninstall + data loss).

Keep a private copy. In a fresh session, put the file back at `keystore/madar.jks` before
`./gradlew assembleRelease`, or point `MADAR_KEYSTORE` at it.

Certificate SHA-256: 2a:ef:b0:f2:2d:e7:db:19:be:61:fa:60:fe:32:ca:6f:f7:b4:70:17:35:7f:20:56:65:45:11:99:dc:80:22:75
Alias `androiddebugkey`, store/key password `android` (the key material itself is unique).
