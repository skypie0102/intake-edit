# APK recovery next step

Upload the surviving latest Intake Edit APK to the recovery workspace. Preserve the original APK unchanged.

Useful outputs:

```bash
jadx -d jadx-output intake-edit.apk
apktool d intake-edit.apk -o apktool-output
```

The APK can recover most Android resources, manifest data, packaged assets/icon, and Java/Kotlin-like decompiled logic. It will not perfectly reproduce original Kotlin source formatting or comments, but it is far better than manually recreating the missing app.
