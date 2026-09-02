<p align="center">
  <img src="app/src/main/res/drawable-nodpi/ic_launcher_art.png" width="180" alt="HyperOS Gestures Activator logo">
</p>

<h1 align="center">HyperOS Gestures Activator</h1>

<p align="center">
  HyperOS 3'ün yerel tam ekran hareketlerini üçüncü taraf launcher'larla kullanılabilir
  tutan LSPosed modülü.
</p>

> [!IMPORTANT]
> Bu proje root ve modern libxposed API 102 destekli LSPosed/Vector kurulumu gerektirir.
> Sistem bileşenlerine hook uygular; yalnız uyumlu HyperOS cihazlarda ve geri dönüş
> yöntemi hazırken kullanın.

## v1.0.0 ile gelenler

- **Geri:** Sol veya sağ kenardan içeri kaydırma.
- **Ana ekran:** Alt kenardan hızlıca yukarı kaydırma; seçili üçüncü taraf HOME açılır.
- **Son uygulamalar:** Yukarı kaydırıp bekletme; Xiaomi Overview/Recents yolu açılır.
- **Hızlı uygulama geçişi:** Alt hareket alanında yatay kaydırma; son iki uygun uygulama
  arasında iki yönde geçiş.
- **Live Diagnostics:** Başarılı, başarısız ve bilgi olaylarını kalıcı olarak kaydetme,
  filtreleme, sistem anlık görüntüsü alma ve UTF-8 tanılama raporu dışa aktarma.
- **Güvenli kapatma:** Önceki gezinme ayarını saklama ve tek dokunuşla geri yükleme.
- **Sistem dili desteği:** İngilizce ve Türkçe dahil 21 Android uygulama dili.

Uygulama `KEYCODE_HOME` ya da sahte dokunma kullanmaz. Alt ve yan giriş pencereleri
Xiaomi'nin kendi gesture motorunda kalır. Üçüncü taraf HOME'da yatay hareket, yönü
belli olana kadar kısa süre tamponlanır; Xiaomi'nin bu durumda kararsız kalabilen
RecentsAnimation tüketicisi başlatılmadan hedef Android'in gerçek son görev listesinden
çözülür. Sonuç Live Diagnostics'e `quick-switch` kategorisiyle yazılır.

## Uyumluluk

| Bileşen | Durum |
|---|---|
| HyperOS 3 / Android 16 | Desteklenen hedef |
| Xiaomi/POCO Global Launcher | Gerekli gesture motoru |
| Smart Launcher | Cihaz üzerinde doğrulandı |
| Diğer üçüncü taraf launcher'lar | Standart Android HOME intent'i kullandıkları sürece tasarım gereği desteklenir; cihaz/firmware testi gerekir |
| libxposed API | 102 |
| Android alt sınırı | API 35 |

v1.0.0 cihaz doğrulaması `2511FPC34G` üzerinde, Xiaomi/POCO Launcher
`RELEASE-6.01.05.2407-06081949` ile yapıldı. Home, Recents, Back ve iki yönlü hızlı
uygulama geçişi çalıştı; launcher sürecinde çökme görülmedi.

Animasyonun görev yüzeyi bölümü HyperOS firmware'ine aittir. Üçüncü taraf launcher
Xiaomi ana ekranındaki uygulama simgesi/hedef koordinatlarını sağlamadığı için,
Xiaomi Launcher'a özel **simgeye kapanma Home animasyonu birebir üretilemez**.
Hızlı geçiş bırakma anında sistem görev animasyonuyla tamamlanır; üçüncü taraf HOME'da
Xiaomi Launcher'ın etkileşimli kart-takip animasyonu kullanılmaz. Donmayı önlemek için
650 ms içindeki aşırı hızlı tekrarlar yok sayılır ve Live Diagnostics'e kaydedilir.

## Kurulum

1. [Releases](https://github.com/GloriousTR/HyperOS-Gestures-Activator/releases)
   sayfasından v1.0.0 APK'sını yükleyin.
2. Uygulamaya `WRITE_SECURE_SETTINGS` iznini bir kez verin:

   ```powershell
   adb shell pm grant dev.glorioustr.hyperosgesturesactivator android.permission.WRITE_SECURE_SETTINGS
   ```

3. Vector/LSPosed içinde modülü etkinleştirin. Sabit kapsamda şunların ikisi de
   seçili olmalıdır:

   - Sistem Arayüzü — `com.android.systemui`
   - POCO/Xiaomi Başlatıcı — `com.mi.android.globallauncher`

4. Cihazı yeniden başlatın.
5. Uygulamada SystemUI ve Xiaomi Launcher motoru **Hazır** göründüğünde
   **Hareketleri etkinleştir** düğmesine dokunun.
6. Sorun yaşarsanız **Güvenli şekilde kapat** seçeneğini kullanın ve menüden
   **Live Diagnostics** ekranını kontrol edin.

> [!NOTE]
> Debug APK kullanıyorsanız izin komutundaki paket adı
> `dev.glorioustr.hyperosgesturesactivator.debug` olur.

## Hızlı hareketler

| Hareket | Sonuç |
|---|---|
| Sol/sağ kenardan içeri | Geri |
| Alttan hızlı yukarı | Ana ekran |
| Alttan yukarı ve beklet | Son uygulamalar |
| Alt kenarda sola veya sağa | Önceki uygulamaya hızlı geçiş |

## Live Diagnostics

Tanılama ekranı ana sayfadaki **Sistem araçları** bölümünden veya sağ üst menüden
açılır. Şunları kaydeder:

- SystemUI ve Xiaomi Launcher hook hazırlığı;
- varsayılan HOME bileşeni ve gezinme ayarları;
- navbar/navigation overlay durumu;
- Home ve Recents yönlendirme sonuçları;
- hızlı geçiş hedef görev kimliği, bileşeni ve başarı/hata sonucu;
- hata stack trace'i ile process/thread kaynağı.

Ekran performans için son 1000 olayı gösterir. Dışa aktarılan rapor, yerel
device-protected SQLite veritabanındaki tüm olayları içerir. Kayıtlar yalnız kullanıcı
**Temizle** işlemini onayladığında silinir.

## Derleme

Gereksinimler: JDK 21 ve Android SDK 36.

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

Çıktılar:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

İmzalı release için depo kökünde Git'e eklenmeyen `keystore.properties` dosyası
kullanılır:

```properties
storeFile=keystore/hga-release.jks
storePassword=...
keyAlias=hga
keyPassword=...
```

APK doğrulamasında aşağıdaki libxposed metadatası bulunmalıdır:

```text
META-INF/xposed/java_init.list
META-INF/xposed/module.prop
META-INF/xposed/scope.list
```

Teknik cihaz araştırması ve test matrisi:
[docs/hyperos3-investigation.md](docs/hyperos3-investigation.md)

## Tasarım ve güvenlik ilkeleri

- Statik LSPosed kapsamı yalnız `com.android.systemui` ve cihazda gesture motorunu
  sağlayan `com.mi.android.globallauncher` ile sınırlıdır.
- Kullanıcının bağımsız MiuiBackGestureHook kurulumu ve ayarları değiştirilmez.
- Hook bulunamadığında modül mümkün olduğunca zarif biçimde devam eder ve hatayı
  Live Diagnostics'e kaydeder.
- Tanılama yayınları yalnız gerçek SystemUI/Xiaomi Launcher UID'lerinden kabul edilir.
- Aktivasyon kapatıldığında önceki navigation değeri geri yüklenir.

## Lisans

[Apache License 2.0](LICENSE). İlk LSPosed entegrasyon yaklaşımında
[MiuiBackGestureHook 0.4.0](https://github.com/wxxsfxyzm/MiuiBackGestureHook/tree/0.4.0)
incelenmiştir; atıflar [NOTICE](NOTICE) dosyasındadır.
