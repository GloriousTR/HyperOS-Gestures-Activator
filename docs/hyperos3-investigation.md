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

## Live Diagnostics olay modeli

Her olay aşağıdaki alanlarla kalıcı olarak kaydedilir:

- zaman ve artan olay kimliği;
- `SUCCESS`, `FAILURE` veya `INFO` durumu;
- kategori ve işlem adı;
- ayrıntı veya hata stack trace'i;
- kaynak process ve thread.

SystemUI içindeki modül olayları açık hedefli bir yerel broadcast ile uygulamadaki
receiver'a iletilir. Receiver yalnızca Android `SYSTEM_UID` kaynağını kabul eder.
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

## Test matrisi

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
