# Kontrat Yap — Android APK

Uygulama her açılışta `https://kontrat-yap.github.io/` adresinden güncel HTML'yi HTTPS ile indirir.
İnternet yoksa son başarılı sürüm açılır; ilk açılışta internetsizse APK içindeki sürüm açılır.
Kişisel form verileri hiçbir sunucuya gönderilmez. WebView ağ bağlantısı kapalıdır; yalnızca Java kodu şablonu indirir.
PDF düğmesi Android'in yerel yazdırma/PDF kaydetme penceresini açar.
Yeni HTML güncellemesini almak için uygulamayı kapatıp açmak yeterli.
Android Studio'da açılabilir veya JDK 17 ve Gradle 8.11.1 ile `gradle :app:assembleDebug` çalıştırılabilir.
