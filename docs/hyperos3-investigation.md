# HyperOS 3 Navigation Investigation

## Problem tanımı

HyperOS 3, üçüncü taraf bir launcher varsayılan HOME olduğunda gesture modunu veya
navbar overlay'ini üç tuşlu moda döndürüyor. Bu davranış üç ayrı problem olarak ele
alınır:

1. **Back gesture** — cihazda MiuiBackGestureHook 0.4.0 ile çalışıyor.
2. **Navbar visibility** — HGA'nın ilk çözüm hedefi.
3. **Home / Recents / Quick Switch** — navbar unlock doğrulandıktan sonra WM Shell
   ve Quickstep katmanında ele alınacak.

## v0.1.0 başarı kriteri

Bu build hiçbir sistemi değiştirmez. Xiaomi Launcher → üçüncü taraf launcher geçişi
sırasında aşağıdaki geçişlerden en az birini kanıtlayan log üretmelidir:

- bir navigation ayarının değişmesi;
- `navbar.gestural` / `navbar.threebutton` overlay durumunun değişmesi;
- `NavigationModeController` içinde interaction mode değişmesi;
- launcher uygunluk kontrolü veya gesture modundan çıkış metodunun çağrılması.

Logların değişiklik anını yakalaması, v0.2.0 hook'unun sınıf ve metot adını tahmin
etmek yerine cihazın gerçek HyperOS build'ine göre seçmemizi sağlar.

## v0.2.0 cihaz bulgusu ve çözüm

POCO/Xiaomi Launcher varsayılan HOME iken alt ve yan gesture pencereleri oluşturulur.
Smart Launcher varsayılan yapıldığında iki ayrı üretici kontrolü devreye girer:

1. `PhoneStateMonitorController$2.run()` SystemUI içinden
   `global/force_fsg_nav_bar=0` yazar.
2. `OverviewComponentObserver.updateOverviewTargets()` Xiaomi Launcher içinden
   `BaseRecentsImpl.setIsUseMiuiHomeAsDefaultHome(false)` çağırır; bunun sonucunda
   Home, sol Back ve sağ Back gesture pencereleri kaldırılır.

v0.2.0, yalnız uygulama aktivasyonu açıkken bu iki kararı korur. Ayrıca Xiaomi'nin
`NavStubView.performAppToRecents(boolean)` metodu üçüncü taraf HOME durumunda
`mLauncher=null` nedeniyle Recents yerine Home'a döndüğü için bırakma anı resmi
`OverviewCommandHelper` fallback yoluna verilir. Hedef ekran Xiaomi
`RecentsActivity`'dir ve WM Shell RecentsAnimation başlangıcı korunur. Aynı üçüncü
taraf durumda `performAppToHome()` bırakma anı,
`OverviewComponentObserver.getHomeIntent()` ile çözülen gerçek varsayılan HOME
intent'ine yönlendirilir. Her iki rotada da `KEYCODE_HOME` kullanılmaz.

Aktivasyon, her açılış için iki hazır işareti ister:

- `hga_systemui_hook_ready=v0.2.0:<boot_count>`
- `hga_launcher_hook_ready=v0.2.0:<boot_count>`

Bu işaretlerden biri yoksa uygulama navbarı gizlemez. Güvenli kapatma önce
`hga_gesture_activation_enabled=0` yazar, sonra aktivasyondan önceki
`force_fsg_nav_bar` değerini geri yükler.

## Live Diagnostics olay modeli

Her olay aşağıdaki alanlarla kalıcı olarak kaydedilir:

- zaman ve artan olay kimliği;
- `SUCCESS`, `FAILURE` veya `INFO` durumu;
- kategori ve işlem adı;
- ayrıntı veya hata stack trace'i;
- kaynak process ve thread.

SystemUI ve Xiaomi Launcher içindeki modül olayları açık hedefli bir yerel broadcast
ile uygulamadaki receiver'a iletilir. Receiver yalnızca cihazdaki gerçek SystemUI
ve Xiaomi Launcher UID'lerini kabul eder; receiver ayrıca sistem ayrıcalıklı yayın
izniyle korunur.
Veritabanı device-protected storage içinde olduğu için SystemUI'nin erken açılış
olayları kullanıcı kilidi açılmadan da kaydedilebilir.

Live ekran 750 ms aralıkla yenilenir. Performans için son 1000 olay gösterilir,
ancak kullanıcı “Kayıtları temizle” işlemini onaylayana kadar eski olaylar
veritabanından silinmez.

## Kaydedilen state

### Settings

- Secure: `force_fsg_nav_bar`
- Secure: `navigation_mode`
- Secure: `navigation_bar_mode`
- Secure: `miui_fullscreen_gesture`
- Secure: `system_navigation_keys_enabled`
- Secure: `gesture_navigation_bar`
- Global: `force_fsg_nav_bar`
- Global: `navigation_mode`
- Global: `policy_control`
- Global: `navigationbar_is_min`
- System: `force_fsg_nav_bar`

Bir anahtarın `null` olması hata değildir; Xiaomi sürümleri aynı state'i farklı
namespace veya alanlarda tutabilir.

### SystemUI

`com.android.systemui.navigationbar.NavigationModeController` içindeki aşağıdaki
metot adları varsa sadece giriş/çıkışları izlenir:

- `updateCurrentInteractionMode`
- `updateInteractionMode`
- `onDefaultDisplayChanged`
- `onOverlayChanged`
- `deferGesturalNavOverlayIfNecessary`
- `restoreGesturalNavOverlayIfNecessary`
- `setModeOverlay`
- `isGestureNavSupportedByDefaultLauncher`
- `switchFromGestureNavModeIfNotSupportedByDefaultLauncher`
- `onNavigationModeChanged`

Metot bulunmaması modülü durdurmaz; bu da cihaz uyumluluk bulgusudur.

## v1.0.0 işlev testi

Varsayılan HOME üçüncü taraf launcher iken aşağıdakilerin tamamı doğrulanmalıdır:

| Test | Beklenen sonuç |
|---|---|
| **Hareketleri aç** | `activation=1`, `force_fsg_nav_bar=1`, `navigation_mode=2` |
| Kısa alt kaydırma | Üçüncü taraf HOME açılır |
| Alt kaydır ve beklet | Xiaomi `RecentsActivity` açılır |
| Sol/sağ kenardan içeri kaydırma | Geri işlemi çalışır |
| Alt kenarda yatay kaydırma | Android son görev listesindeki önceki uygun uygulamaya geçilir; ters yönde kaydırma son iki uygulama arasında geri döner |
| **Güvenli kapat** | Önceki navbar modu döner, gesture pencereleri kaldırılır |
| Yeniden başlatma | Aynı HOME korunur ve aktivasyon kendiliğinden geri gelir |

WindowManager'da aktif durumda `GestureStubHome`, `GestureStubLeft` ve
`GestureStubRight` pencerelerinin üçü de bulunmalıdır.

v1.0.0'da alt hareket, `NavStubView.onTouchEvent` girişinde yön belli olana kadar
tamponlanır. Dikey hareketler özgün Xiaomi Home/Recents hattına aynen devredilir.
Yatay hareketlerde ise üçüncü taraf HOME ile güvenilir biçimde tamamlanmayan Xiaomi
RecentsAnimation başlatılmaz; hedef launcher sürecinin erişebildiği Android görev
listesinden çözülür. HOME, Xiaomi Launcher ve çalışmakta olan görev elenir; seçilen
önceki uygulama `moveTaskToFront` ile sistem görev animasyonu kullanılarak öne alınır.
Hedef çözümü ve sonuç Live Diagnostics'e `quick-switch` kategorisiyle kaydedilir.
650 ms içindeki tekrarlar, `recents_animation_input_consumer` yarışını ve sistem ANR'ını
önlemek için yok sayılır. Xiaomi ana ekranına özgü simgeye kapanma animasyonu üçüncü
taraf launcher tarafından sağlanamaz.

## v0.1.0 test matrisi

Her satır için launcher seçildikten sonra 10 saniye bekleyin ve navbar görünümüyle
birlikte log zamanını not edin.

| Durum | Varsayılan HOME | Beklenen Back | Beklenen navbar |
|---|---|---:|---:|
| A | Xiaomi Launcher | Çalışır | Gesture/hidden |
| B | Üçüncü taraf launcher | MiuiBackGestureHook ile çalışır | Üç tuş görünür |
| C | Xiaomi Launcher'a dönüş | Çalışır | Gesture/hidden |

Önerilen üçüncü taraf launcher sırası: cihazda asıl kullanılan launcher, ardından
yalnızca gerekirse ikinci bir launcher. İlk teşhiste bir launcher yeterlidir.

## v0.2.0 karar kuralı

- Launcher uygunluk kontrolü açıkça `false` dönüyorsa yalnızca o karar yolu için
  dar kapsamlı hook değerlendirilir.
- Gesture modundan çıkış metodu çağrılıyorsa NO-OP yaklaşımı, ayarı sürekli yazan
  bir watchdog'dan daha temiz olabilir.
- Sadece settings/overlay değişiyorsa önce değişikliği yapan process ve çağrı yolu
  kanıtlanır; körlemesine `force_fsg_nav_bar=1` döngüsü eklenmez.
- `system_server` scope'u ancak SystemUI kanıtları framework tarafını işaret ederse
  eklenir.

## Log paylaşımı

LSPosed/Vector modül logunda `HGA/Diagnostics` ile filtreleyin. Paylaşmadan önce
istenmeyen cihaz veya uygulama bilgilerini kontrol edin. HGA uygulama ekranındaki
snapshot yardımcıdır; karar için esas veri SystemUI sürecindeki modül logudur.
