# Değişiklik günlüğü

## 1.0.0 — 2026-09-02

- Üçüncü taraf HOME kullanılırken alt kenarda iki yönlü hızlı uygulama geçişi eklendi.
- Yatay ve dikey alt hareketler, yön belirlenene kadar güvenli bir sınıflandırıcıyla
  ayrılıyor; Home/Recents akışı değiştirilmeden korunuyor.
- Yatay harekette kararsız Xiaomi RecentsAnimation başlatılmadan geçiş hedefi
  Android'in son görev listesinden çözümleniyor.
- Hızlı geçiş hedefi, bileşeni ve başarı/hata sonucu Live Diagnostics'e ekleniyor.
- Ardışık uzak animasyonların sistem girişini kilitlemesini önlemek için hızlı geçişte
  650 ms güvenlik aralığı uygulanıyor.
- Home, Recents ve Back yönlendirmeleri korunuyor.
- Live Diagnostics rapor dışa aktarma ve kalıcı olay geçmişi tamamlandı.
- Profesyonel kontrol paneli, Hakkında ekranı, uyumlu uygulama simgesi ve 21 sistem
  dili desteği eklendi.

## 0.2.0

- Üçüncü taraf launcher ile HyperOS gesture motoru aktivasyonu eklendi.
- Home ve Overview fallback yönlendirmeleri ile güvenli aç/kapat akışı eklendi.

## 0.1.0

- Salt okunur HyperOS gezinme tanılaması ve LSPosed logları eklendi.
