# راه‌اندازی حافظهٔ ابری، اعلان‌ها و به‌روزرسانی هیکتور

## ۱. Firebase و همگام‌سازی حافظه

1. در Firebase Console یک پروژه بساز و Android app با شناسهٔ دقیق `com.hector.android` اضافه کن.
2. فایل `google-services.json` را از تنظیمات همان Android app دریافت و در مسیر `app/google-services.json` قرار بده. این فایل شناسه‌های پروژه را دارد؛ هیچ Firebase Admin service-account key را داخل APK یا مخزن قرار نده.
3. در Firebase Authentication، روش Email/Password را فعال کن.
4. در Firestore Database یک پایگاه داده ایجاد کن.
5. قوانین موجود در `firestore.rules` را در بخش Firestore Rules منتشر کن. این قوانین دسترسی هر حساب را به مسیر UID خودش محدود می‌کند.
6. APK را بساز و نصب کن. در هیکتور «همگام‌سازی ابری حافظه» را انتخاب کن و حساب بساز یا وارد شو. همگام‌سازی خودکار بعد از ورود فعال می‌شود.
7. فقط یادداشت حافظه و تاریخچهٔ گفت‌وگو همگام می‌شوند؛ کلید API، نشانی سرویس و نام مدل ارسال نمی‌شوند. محتوای حافظه در فضای ابری به Firestore فرستاده می‌شود؛ قوانین دسترسی و رمزگذاری سمت Firebase جایگزین رمزگذاری سرتاسری با کلیدی که فقط خود کاربر دارد نیستند.

## ۱-الف. اتصال امن Firebase به GitHub Actions

برای اینکه APK ساخته‌شده در GitHub Actions واقعاً به پروژهٔ Firebase متصل شود، قرار دادن فایل فقط روی گوشی یا در Termux کافی نیست؛ Runnerهای GitHub محیط موقت و جداگانه دارند.

1. در Firebase Console پروژهٔ `hector-9b819` را باز کن و مطمئن شو Android app دقیقاً با شناسهٔ `com.hector.android` ثبت شده است.
2. فایل `google-services.json` همان برنامه را دانلود کن. آن را در مخزن عمومی commit نکن.
3. در GitHub به **Settings → Secrets and variables → Actions → New repository secret** برو و Secret زیر را بساز:
   - نام: `HECTOR_GOOGLE_SERVICES_JSON_BASE64`
   - مقدار: محتوای Base64 کامل فایل `google-services.json`؛ نه متن JSON خام و نه مسیر فایل.
4. در Termux، Base64 را محلی تولید کن و فقط خروجی را در فیلد Secret خود GitHub قرار بده. آن را در گفتگو، Issue، لاگ یا فایل commit‌شده قرار نده. نمونهٔ محلی:
   ```sh
   base64 -w 0 app/google-services.json
   ```
   اگر نسخهٔ `base64` گزینهٔ `-w` را پشتیبانی نکند، از `base64 app/google-services.json | tr -d '\\n'` استفاده کن.
5. دوباره GitHub Actions را اجرا کن. مرحلهٔ `Configure Firebase Android resources` باید پیام اعتبارسنجی `com.hector.android` بدهد و دیگر نباید هشدار «Firebase cloud sync and push registration will be unavailable» ظاهر شود.
6. در Firebase Authentication روش **Email/Password** را فعال کن. در Firestore مطمئن شو پایگاه دادهٔ پیش‌فرض ساخته شده و فایل `firestore.rules` همین مخزن را منتشر کن. قوانین تازه فقط سند حافظهٔ مجاز و توکن‌های دستگاهِ متعلق به UID همان کاربر را می‌پذیرند.

**مهم:** داشتن `google-services.json` به‌تنهایی اثبات نمی‌کند که ورود، Firestore یا تحویل FCM در گوشی کار می‌کند. این موارد به‌ترتیب با ورود واقعی، همگام‌سازی داده و ارسال آزمایشی از سرویس مورداعتماد سمت سرور بررسی می‌شوند.

## ۲. اعلان‌های پوش

1. در همان پروژه Firebase، Cloud Messaging را فعال کن.
2. در هیکتور «فعال‌سازی اعلان‌های هیکتور» را بزن و اجازهٔ اعلان را بده.
3. برای دریافت اعلان، توکن دستگاه باید به یک سرویس مورداعتماد ثبت شود. برای ارسال واقعی پیام، یک Firebase Cloud Function یا سرور مورداعتماد با Firebase Admin SDK لازم است. کلید Admin فقط در سرور نگهداری شود، نه در اپ اندروید.
4. مجوز اعلان Android 13 به بالا باید توسط کاربر پذیرفته شود.

## ۳. به‌روزرسانی رسمی

1. در Google Play Console برنامه را با شناسهٔ `com.hector.android` ایجاد کن.
2. نسخهٔ امضاشده را در مسیر انتشار داخلی آزمایش کن و همان حساب Google را به فهرست آزمایش‌کنندگان اضافه کن.
3. نصب اولیه باید از Google Play انجام شود. APK نصب‌شده از GitHub/Termux واجد شرایط Play In-App Updates نیست.
4. برنامه در بازگشت به پیش‌زمینه، وضعیت به‌روزرسانی Play را بررسی می‌کند؛ دانلود نسخهٔ جدید و نصب آن تابع Play و تأییدهای لازم کاربر است.

## ۴. آزمون نصب

GitHub Actions اکنون APK را می‌سازد، امضا را بررسی می‌کند و تلاش می‌کند APK را روی Android Emulator نصب و اجرا کند. این آزمون، نصب/راه‌اندازی خودکار است و جای آزمون میکروفون و ادامهٔ مکالمه روی گوشی واقعی را نمی‌گیرد.
