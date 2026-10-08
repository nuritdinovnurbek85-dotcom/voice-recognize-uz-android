# voice.Recognize.uz — Android ilova

Kompyuterdagi **voice.Recognize.uz** serveriga Wi-Fi orqali ulanadigan mobil ilova:
nutqni matnga o'tkazish (UZ/RU/EN), tarjima, matnni nutqqa o'tkazish, ijro tezligi.

## APK olish (GitHub orqali, kompyuterga hech narsa o'rnatmasdan)
1. github.com da hisob oching → **New repository** → nomi `voice-recognize-uz-android` → **Create**.
2. **uploading an existing file** havolasini bosing → shu papkadagi BARCHA fayl va papkalarni
   (`.github` ham) sudrab tashlang → **Commit changes**.
   `.github` papkasi ko'rinmasa: Windows'da "Yashirin elementlar"ni yoqing.
3. **Actions** bo'limi → "APK yig'ish" ishi avtomatik boshlanadi (5–8 daqiqa).
4. Yashil ✓ belgisi chiqqach, ishni oching → pastda **Artifacts** → `voice-recognize-uz-apk` ni yuklab oling
   (zip ichida `app-debug.apk`).

## Android Studio orqali (muqobil)
File → Open → shu papka → Build → Build App Bundle(s)/APK(s) → Build APK(s).

## Telefonga o'rnatish
APK ni telefonga o'tkazing → oching → "Noma'lum manbalardan o'rnatish"ga ruxsat bering → O'rnatish.

## Ishlatish
1. Kompyuterda `voice_recognize_uz` papkasidagi **run_mobile.bat** ni ishga tushiring.
   U `http://192.168.x.x:5000` ko'rinishidagi manzilni ko'rsatadi.
2. Telefon va kompyuter **bitta Wi-Fi** tarmog'ida bo'lsin.
3. Ilovada "Server manzili" maydoniga shu manzilni yozing → **Tekshirish** → "ulandi ✓".
4. **Yozish** → gapiring → **To'xtatish** → matn chiqadi. **Tarjima**, **O'qish** tugmalari ishlaydi.

Windows "Brandmauer" (Firewall) so'rasa — **Xususiy tarmoqlar** uchun ruxsat bering.
