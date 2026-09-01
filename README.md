# HyperOS Gestures Activator

HyperOS 3'te üçüncü taraf bir launcher varsayılan HOME iken yerel hareketle gezinme
altyapısını kullanılabilir tutmayı araştıran LSPosed/Vector modülü.

## Şu anki sürüm: v0.1.0 Navigation Diagnostics

Bu ilk build **salt okunurdur**. Gezinme ayarlarını, overlay'leri veya SystemUI dönüş
değerlerini değiştirmez. Aşağıdaki verileri LSPosed modül loguna kaydeder:

- varsayılan HOME bileşeni;
- `force_fsg_nav_bar`, `navigation_mode` ve Xiaomi'ye özgü ilgili ayarlar;
- aktif navbar/navigation overlay'leri;
- `NavigationModeController` içindeki mod, launcher, overlay ve gesture alanları;
- launcher ve navigation mode değişimlerinde çağrılan ilgili SystemUI metotları.

Bu ayrım bilinçlidir: cihazda çalışan MiuiBackGestureHook 0.4.0 Back hareketini
sağlamaya devam ederken HGA yalnızca üç tuşlu moda dönüşün kaynağını belirler.

## Derleme

Gereksinimler:

- JDK 21 (Gradle toolchain; kaynak uyumluluğu Java 17)
- Android SDK 36

Windows:

```powershell
.\gradlew.bat assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Cihaz test akışı

1. APK'yı yükleyin.
2. LSPosed/Vector içinde modülü etkinleştirin; scope yalnızca `System UI` olmalıdır.
3. SystemUI'yi yeniden başlatın veya cihazı yeniden başlatın.
4. Xiaomi Launcher'ı varsayılan yapın, ardından üçüncü taraf launcher'a geçin.
5. LSPosed modül logunu dışa aktarın ve `HGA/Diagnostics` ile filtreleyin.

Ayrıntılı protokol: [docs/hyperos3-investigation.md](docs/hyperos3-investigation.md)

## Yol haritası

- `v0.1.0`: Navigation Diagnostics — salt okunur state ve karar yolu kaydı.
- `v0.2.0`: Navbar Unlock — doğrulanan SystemUI yolunda üç tuşlu moda dönüşü engelleme.
- `v0.3.0`: Bottom Gesture Probe — alt kenar HOME/RECENTS/QUICK_SWITCH adayları.
- `v0.4.0+`: WM Shell/Quickstep üzerinden native Home, Recents ve Quick Switch.

`KEYCODE_HOME` enjeksiyonu nihai mimari değildir. Hedef SystemUI → WM Shell →
RecentsAnimation/Overview zincirini kullanmaktır.

## Kaynak ve lisans

Apache License 2.0. İlk yapı ve LSPosed API 102 entegrasyon yaklaşımı,
[MiuiBackGestureHook 0.4.0](https://github.com/wxxsfxyzm/MiuiBackGestureHook/tree/0.4.0)
incelenerek oluşturulmuştur. Ayrıntılar [NOTICE](NOTICE) dosyasındadır.
