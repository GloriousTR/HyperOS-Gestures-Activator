# HyperOS Gestures Activator

HyperOS 3'te üçüncü taraf bir launcher varsayılan HOME iken yerel hareketle gezinme
altyapısını kullanılabilir tutmayı araştıran LSPosed/Vector modülü.

## Şu anki sürüm: v0.2.0 Gesture Activation

Bu sürüm, Smart Launcher gibi üçüncü taraf bir HOME kullanılırken HyperOS'un yerel
Home, Son Uygulamalar ve Geri hareketlerini doğrudan kullanılabilir tutar. Açma
işlemi yalnızca SystemUI ile Xiaomi/POCO Launcher korumaları o açılış için hazırsa
çalışır. **Güvenli kapat** düğmesi aktivasyonu kapatır ve önceki gezinme ayarını
geri yükler.

Dar kapsamlı çözüm dört parçadan oluşur:

- SystemUI'nin üçüncü taraf HOME algıladığında gesture modunu zorla kapatan kararını
  yalnız aktivasyon açıkken engellemek;
- Xiaomi/POCO Launcher'ın yerel alt ve yan gesture pencerelerini kullanılabilir
  tutmak;
- üçüncü taraf HOME'da yukarı kaydırıp bekletmeyi Xiaomi'nin resmi fallback
  `RecentsActivity` / Overview yoluna yönlendirmek;
- `force_fsg_nav_bar` değişimini uygulamadaki açık kapalı durumuyla yönetmek.

`KEYCODE_HOME` veya sahte dokunma kullanılmaz. Girdi Xiaomi'nin yerel gesture
pencerelerinden gelir. Üçüncü taraf launcher kullanılırken Home bırakma anı,
Xiaomi `OverviewComponentObserver` nesnesinin belirlediği gerçek varsayılan HOME
intent'ine; Son Uygulamalar ise resmi `OverviewCommandHelper` yoluna verilir. Back
hareketi cihazdaki mevcut Xiaomi/MiuiBackGestureHook davranışını korur.

Profesyonel ana kontrol paneli aktivasyon durumunu, SystemUI/Launcher sağlığını,
varsayılan ana ekranı ve temel hareketleri tek bakışta gösterir. Ayrıntılı olay
akışı ana ekranı kalabalıklaştırmaz; sağ üst menüdeki veya **Sistem araçları**
bölümündeki **Live Diagnostics** seçeneğiyle ayrı ekranda açılır.

Modül aşağıdaki verileri LSPosed/Vector loguna ve Live Diagnostics ekranına kaydeder:

- varsayılan HOME bileşeni;
- `force_fsg_nav_bar`, `navigation_mode` ve Xiaomi'ye özgü ilgili ayarlar;
- aktif navbar/navigation overlay'leri;
- `NavigationModeController` içindeki mod, launcher, overlay ve gesture alanları;
- launcher ve navigation mode değişimlerinde çağrılan ilgili SystemUI metotları.

Uygulamadaki **Live Diagnostics** ekranı SystemUI olaylarını 750 ms aralıkla yeniler:

- başarılı, başarısız ve bilgi olaylarını ayrı renklerle gösterir;
- toplam/başarılı/başarısız/bilgi sayaçları sunar;
- durum türüne göre filtreleme yapar;
- hata stack trace'lerini ve olayın process/thread kaynağını saklar;
- olayları device-protected SQLite veritabanında kalıcı tutar;
- tüm cihaz/aktivasyon özetini ve sınırsız olay geçmişini zaman damgalı UTF-8 metin
  raporu olarak kullanıcının seçtiği konuma dışa aktarır;
- SystemUI ve Xiaomi Launcher'ın gerçek UID'leri dışındaki diagnostics
  göndericilerini reddeder.

Ekran performans için son 1000 olayı gösterir; dışa aktarılan rapor veritabanındaki
tüm olayları içerir. Veritabanı kullanıcı açıkça temizleyene kadar kayıtları
saklamaya devam eder.

Kullanıcının ayrıca kurulu MiuiBackGestureHook 0.4.0 modülüne, ayarlarına veya
kapsamına dokunulmaz.

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
2. Uygulamaya bir kez `WRITE_SECURE_SETTINGS` izni verin:

   ```powershell
   adb shell pm grant dev.glorioustr.hyperosgesturesactivator.debug android.permission.WRITE_SECURE_SETTINGS
   ```

3. Vector/LSPosed içinde modülü etkinleştirin ve sabit kapsamda hem **Sistem
   Arayüzü** (`com.android.systemui`) hem **POCO/Xiaomi Başlatıcı**
   (`com.mi.android.globallauncher`) seçili olduğunu doğrulayın.
4. Cihazı yeniden başlatın. Varsayılan HOME üçüncü taraf launcher olarak kalabilir.
5. Uygulamada SystemUI ve Launcher yanında `Hazır` görünce **Hareketleri
   etkinleştir** düğmesine
   dokunun.
6. Sorun olursa **Güvenli şekilde kapat** düğmesine dokunun ve ana menüden Live
   Diagnostics ekranını açarak son başarısız işlemi inceleyin.

Ayrıntılı protokol: [docs/hyperos3-investigation.md](docs/hyperos3-investigation.md)

## Yol haritası

- `v0.1.0`: Navigation Diagnostics — salt okunur state ve karar yolu kaydı.
- `v0.2.0`: Gesture Activation — Home, Recents ve Back; güvenli aç/kapat ve canlı
  teşhis.
- `v0.3.0`: Quick Switch doğrulaması, cihaz uyumluluk profilleri ve daha ayrıntılı
  sağlık kontrolleri.
- `v0.4.0+`: Ek HyperOS/launcher sürümleri için taşınabilir hook seçimi.

`KEYCODE_HOME` enjeksiyonu kullanılmaz. Son Uygulamalar için resmi
RecentsAnimation/Overview zinciri, Home için sistemin gözlemlediği varsayılan HOME
intent'i kullanılır.

## Kaynak ve lisans

Apache License 2.0. İlk yapı ve LSPosed API 102 entegrasyon yaklaşımı,
[MiuiBackGestureHook 0.4.0](https://github.com/wxxsfxyzm/MiuiBackGestureHook/tree/0.4.0)
incelenerek oluşturulmuştur. Ayrıntılar [NOTICE](NOTICE) dosyasındadır.
