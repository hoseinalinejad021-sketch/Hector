# نمونه تشخیص آسیب محصول برای هیکتور

این فایل نمونه‌های ارسالی را به‌عنوان نمونه آزمون ثبت می‌کند. این نمونه به‌تنهایی قابلیت هوش مصنوعی را به APK وصل نمی‌کند.

## ۱) درخواست ارسالی (نمونه خام)

IMAGE_BASE64="$(base64 < product.png | tr -d '\r\n')"

curl https://api.openai.com/v1/decisions \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -H "Content-Type: application/json" \
  --data-binary @- <<JSON
{
  "model": "gpt-6-luna",
  "input": [{
    "role": "user",
    "content": [
      {"type": "input_text", "text": "Inspect the product in this photo."},
      {"type": "input_image", "image_url": "data:image/png;base64,$IMAGE_BASE64"}
    ]
  }],
  "questions": [{
    "type": "predicate",
    "name": "visible_damage",
    "instructions": "Does the product have visible damage, such as a crack, tear, or dent? Ignore shadows and damage to the packaging."
  }]
}
JSON

این درخواست خام برای استفاده تولیدی تأیید نشده است: مسیر /v1/decisions و نام مدل gpt-6-luna نباید بدون تأیید رسمی معتبر بودنشان استفاده شوند. برای درخواست معمول مدل، از Responses API و نام مدلی که در حساب API واقعاً در دسترس است استفاده شود. قالب پرسش predicate و پاسخ احتمال، قالب استاندارد پاسخ متنی معمولی نیست.

## ۲) پاسخ ارسالی (نمونه خام)

{
  "answers": [
    {
      "type": "predicate",
      "name": "visible_damage",
      "probability": 0.92
    }
  ]
}

تفسیر نمونه: خروجی ادعا می‌کند احتمال تشخیص آسیب قابل‌مشاهده ۰٫۹۲ (۹۲٪) است. این عدد فقط نتیجه نمونه ارسالی است و به‌تنهایی صحت مدل، تصویر یا اتصال API را اثبات نمی‌کند.


## ۳) نمونهٔ دسته‌بندی درخواست پشتیبانی (پاسخ خام ارسالی)

```json
{
  "answers": [
    {
      "type": "choice",
      "name": "department",
      "choice": "billing",
      "probabilities": [
        { "value": "billing", "probability": 0.95 },
        { "value": "technical", "probability": 0.02 },
        { "value": "shipping", "probability": 0.01 },
        { "value": "other", "probability": 0.02 }
      ],
      "confidence": 0.93
    }
  ]
}
```

تفسیر نمونه: این خروجی، «billing» (صورتحساب) را به‌عنوان دستهٔ منتخب گزارش می‌کند؛ احتمال‌های فهرست‌شده جمعاً ۱٫۰۰ هستند و مقدار confidence برابر ۰٫۹۳ است. این دو عدد الزاماً معنای یکسانی ندارند و بدون مستندات مدل نباید آن‌ها را معادل دانست. این JSON به‌تنهایی صحت اتصال API یا کالیبره‌بودن احتمال‌ها را ثابت نمی‌کند. اگر از آن در هیکتور استفاده شود، باید صرفاً پیشنهاد دسته‌بندی باشد و برای موارد مبهم امکان انتخاب «سایر» یا اصلاح توسط کاربر وجود داشته باشد.


## ۴) نمونهٔ امتیازدهی شدت خطا

ورودی نمونه: «Export fails in Safari but works in Chrome.» گزینه‌های شدت عبارت‌اند از Cosmetic، Workaround available و Fully blocked.

تفسیر منطقی متن: **Workaround available** (راه جایگزین وجود دارد)، چون خروجی گرفتن در Safari شکست می‌خورد اما در Chrome کار می‌کند. این نتیجه استنباط از متن است، نه پاسخ واقعی تأییدشده از API؛ در پیام ارسالی هیچ پاسخ سروری نشان داده نشده است. پیش از استفاده از endpoint و نام مدل نمونه، اعتبار آن‌ها را با مستندات رسمی و دسترسی حساب بررسی کنید. اگر هیکتور چنین امتیازدهی‌ای را اجرا کند، باید نتیجه را فقط به‌عنوان پیشنهاد نمایش دهد و امکان اصلاح دستی فراهم باشد؛ نتیجه نباید مجوز عملیات حساس یا تغییر خودکار باشد.


## ۵) نمونهٔ پاسخ امتیازدهی شدت خطا (JSON ارسالی)

```json
{
  "answers": [
    {
      "type": "score",
      "name": "severity",
      "score": 1.1,
      "probabilities": [
        { "value": 0, "label": "Cosmetic", "probability": 0.1 },
        { "value": 1, "label": "Workaround available", "probability": 0.7 },
        { "value": 2, "label": "Fully blocked", "probability": 0.2 }
      ],
      "confidence": 0.55
    }
  ]
}
```

تفسیر: امتیاز ۱٫۱ روی مقیاس ۰ تا ۲ به «Workaround available» نزدیک‌تر است و با بیشترین احتمال دسته‌ای (۰٫۷) نیز همین گزینه انتخاب می‌شود. با این حال، confidence برابر ۰٫۵۵ نشان می‌دهد که نباید نتیجه را قطعی جلوه داد. معنی دقیق score و confidence وابسته به قرارداد API و مدل است؛ این JSON به‌تنهایی پاسخ واقعیِ تأییدشده از سرویس یا کالیبره‌بودن احتمال‌ها را ثابت نمی‌کند. در هیکتور باید نتیجه فقط پیشنهاد باشد و امکان اصلاح دستی وجود داشته باشد؛ امتیاز نباید به‌تنهایی عملیاتی حساس را اجرا کند.


## ۶) نمونهٔ واگذاری درخواست راننده به تصمیم‌گیری

نمونهٔ ارسالی از مدل مکالمه‌ای `gpt-live-1` می‌خواهد درخواست راننده را به تصمیم‌گیری `gpt-6-luna` واگذار کند. سه خروجیِ دسته‌بندی‌شده عبارت‌اند از:
- `cabin_temperature`: یکی از `temperature_up`، `temperature_down` یا `none`.
- `windshield_defogger`: یکی از `on`، `off` یا `none`.
- `needs_response`: اگر درخواست شامل مسیریابی، جست‌وجوی محلی یا موضوعی فراتر از کنترل‌های کابین باشد `yes`؛ در غیر این صورت `no`.

این قطعه فقط نمونهٔ پیکربندی ارسالی است؛ ثبت آن در مخزن، پشتیبانی واقعی API، اتصال به کنترل‌های خودرو، مسیریابی یا جست‌وجوی محلی را اثبات نمی‌کند. خروجی مدل، قصد احتمالی راننده را دسته‌بندی می‌کند و گزارش اجرای واقعی نیست. هیکتور نباید بگوید دما یا گرم‌کن شیشه تغییر کرده، مگر آن‌که برنامهٔ متصل اجرای موفق را گزارش کرده باشد. هر کنترل واقعی خودرو باید از مسیر مجاز و ایمن برنامه انجام شود؛ خروجی مدل به‌تنهایی مجوز عملیات حساس نیست و نباید قفل حساس یا توقف اضطراری `HIC-STOP-01` را دور بزند.


## ۷) رویداد به‌روزرسانی وضعیت کابین

نمونهٔ ارسالی:
```json
{
  "type": "decision.item.create",
  "event_id": "evt_cabin_after_temperature_up_1",
  "item": {
    "type": "message",
    "role": "user",
    "content": [
      {
        "type": "input_text",
        "text": "Use the latest cabin state snapshot as the current state. Cabin temperature is set to 22 °C in automatic mode. Windshield defogger is off."
      }
    ]
  }
}
```

تفسیر وضعیتِ گزارش‌شده در متن رویداد: دمای تنظیم‌شدهٔ کابین ۲۲ درجهٔ سانتی‌گراد، حالت خودکار؛ بخارزدایی شیشهٔ جلو خاموش. شناسهٔ رویداد `evt_cabin_after_temperature_up_1` نامی است که در نمونه آمده و به‌تنهایی ثابت نمی‌کند فرمان افزایش دما واقعاً اجرا شده یا این snapshot از خودرو دریافت شده است. برنامه باید منبع و تازگی snapshot را اعتبارسنجی کند و فقط پس از تأیید وضعیت واقعی آن را به‌عنوان وضعیت جاری اعلام کند. این نمونه هیچ اتصال واقعی به سخت‌افزار خودرو ایجاد نمی‌کند و مجوزی برای عملیات حساس نیست.


## ۸) توضیح تکمیلیِ گزارش راننده

نمونهٔ ارسالی:
```json
{
  "type": "session.commentary.append",
  "delegation_id": "del_cabin_1",
  "content": "I raised the cabin temperature and confirmed the new setting on the dashboard."
}
```

این پیام می‌گوید راننده گزارش کرده دمای کابین را افزایش داده و تنظیم جدید را روی داشبورد تأیید کرده است. این یک گزارش متنی در نشست است؛ به‌خودی‌خود تأیید ماشینی از وضعیت خودرو، اجرای فرمان توسط هیکتور، یا صحت snapshot نیست. پاسخ هیکتور باید آن را به‌عنوان گزارش راننده نسبت دهد و از ادعای تأیید مستقل خودداری کند، مگر آن‌که منبع وضعیت معتبر و متصل نتیجه را تأیید کند. این پیام نیز نباید قفل عملیات حساس یا توقف اضطراری `HIC-STOP-01` را دور بزند.


## ۹) نمونهٔ Responses API با ابزارهای مسیریابی و جست‌وجوی رستوران

این نمونه از `gpt-6.1-sol` در `/v1/responses` و دو ابزار تابعیِ برنامه‌نویس‌تعریف‌شده استفاده می‌کند: `start_navigation(destination)` و `find_restaurants(location)`. مدل می‌تواند درخواست راننده را تحلیل کرده و فراخوانی ابزار پیشنهاد کند؛ اما خودِ تعریف ابزار، اجرای آن را انجام نمی‌دهد. برنامه باید فراخوانی تابع را دریافت کند، اعتبارسنجی کند، تابع واقعی را اجرا کند، نتیجه را با `function_call_output` برگرداند و سپس پاسخ نهایی مدل را بخواند. برای جست‌وجوی رستوران باید دادهٔ واقعی از منبع جست‌وجو بیاید؛ برای مسیریابی نیز باید نتیجهٔ واقعی برنامهٔ ناوبری بررسی شود.

در متن نمونه، نتیجهٔ کنترل کابین از قبل گزارش شده است: بخارزدایی شیشهٔ جلو روشن و دما بدون تغییر. پاسخ نهایی باید همین گزارش را از نتیجهٔ اجرای ابزارها جدا نگه دارد. تا وقتی خروجی واقعی ابزار دریافت نشده، هیکتور نباید ادعا کند مسیر شروع شده یا رستوران‌هایی پیدا شده‌اند. پیش از شروع مسیریابی، مقصد را از درخواست کاربر بگیرید و وضعیت اجرای واقعی را تأیید کنید. تعریف این نمونه در مستندات به‌معنای فعال‌شدن مسیریابی، جست‌وجوی محلی یا کنترل خودرو در APK فعلی نیست. قفل عملیات حساس و توقف اضطراری `HIC-STOP-01` باید حفظ شوند.

مرجع رسمی: مستندات [Responses API](https://developers.openai.com/api/reference/resources/responses/methods/create)، [راهنمای ابزارهای تابعی](https://developers.openai.com/api/docs/guides/tools)، و [مدل GPT-6.1 Sol](https://developers.openai.com/api/docs/models/gpt-6.1-sol).


## ۱۰) نمونهٔ انتخاب حالت چهره و حرکت متناسب با متن گفتار

نمونهٔ ارسالی از رویداد `session.input_transcript.delta` سه برچسب پیشنهادی تولید می‌کند:
- `expression`: یکی از `neutral`، `happy`، `sad` یا `concerned`.
- `intensity`: یکی از `low`، `medium` یا `high`.
- `gesture`: یکی از `none`، `nod`، `shake` یا `lean`.

این تنظیم صرفاً یک طرح برای طبقه‌بندی متن گفتار است؛ به‌خودی‌خود تشخیص احساس واقعی شخص، تحلیل ویدئو/چهره، یا اجرای حرکت روی آواتار را پیاده‌سازی نمی‌کند. چون رویداد `delta` ممکن است متن ناقص و در حال دریافت باشد، برنامه باید از واکنش شدید یا قطعی به قطعهٔ ناقص پرهیز کند و در صورت نیاز تا دریافت متن کامل‌تر صبر کند. این برچسب‌ها باید حالت نمایشی پیشنهادی باشند، نه ادعای قطعی دربارهٔ احساس شخص؛ انتخاب `neutral` یا `none` باید همیشه مجاز بماند. هیچ ژست یا حالت چهره‌ای نباید عملیات حساس را آغاز یا مجاز کند. این نمونه به‌تنهایی اثبات نمی‌کند قابلیت صوتی زنده یا آواتار در APK فعلی پیاده‌سازی شده است.


## ۱۱) نمونهٔ رویداد تکمیل‌شدهٔ تصمیم حالت چهره و ژست

نمونهٔ ارسالی رویداد `decision.event` با منبع `session.input_transcript.delta` و شناسهٔ رویداد منبع `evt_input_42` را نشان می‌دهد. خروجی تصمیم:
- `expression = happy`
- `intensity = medium`
- `gesture = nod`

این فقط خروجیِ گزارش‌شدهٔ تصمیم‌گیری است. به‌تنهایی ثابت نمی‌کند که متن کامل گفتار دریافت شده، احساس واقعی شخص خوشحالی بوده، یا آواتار واقعاً سر تکان داده است. اگر خروجی برای نمایش آواتار استفاده شود، آن را به‌عنوان پیشنهاد نمایشی اجرا کنید، شدت را متناسب نگه دارید و در صورت ناقص‌بودن متن یا نامطمئن‌بودن زمینه از حالت خنثی/بدون ژست استفاده کنید. اجرای ژست باید از وضعیت واقعی آواتار تأیید شود؛ هرگز از این تصمیم برای مجوزدهی به عملیات حساس استفاده نشود.


## ۱۲) صداهای سفارشی برای هیکتور (Custom Voices)

مستندات ارسالی دو مسیر را شرح می‌دهد:

1. **ساخت صدا از توصیف متنی (prompt-to-voice):** درخواست به `POST /v1/audio/voices` با `type: "prompt"`، نام و توصیف صدای بزرگسال. پاسخ شامل شناسهٔ صداست و خودِ فایل صوتی یا پیش‌نمایش را برنمی‌گرداند. این نوع صدا برای GPT-Live است و طبق متن ارسالی در Text-to-Speech، Realtime یا Chat Completions پشتیبانی نمی‌شود. ساخت صدا رایگان توصیف شده، اما استفاده از آن در GPT-Live با نرخ عادی نشست محاسبه می‌شود.

2. **ساخت صدا از ضبط:** نیازمند فایل رضایت جداگانه و نمونهٔ صوتی همان گوینده است. این قابلیت فقط برای سازمان‌ها/حساب‌های واجد شرایط فعال است و ممکن است نیاز به دسترسی ویژه داشته باشد. برای تقلید صدای دیگران بدون رضایت اقدام نکنید.

برای انتخاب صدای ذخیره‌شده در GPT-Live، شناسهٔ آن در تنظیم اولیهٔ نشست در `audio.output.voice.id` قرار می‌گیرد و توصیف اصلی صدا نیز باید در دستورالعمل‌های نشست حفظ شود. کلید API باید روی سرویس پشتیبان امن بماند؛ آن را در APK یا مخزن قرار ندهید. به شنونده شفاف گفته شود صدا توسط هوش مصنوعی تولید شده و هر فراداده یا نشان منشأ مصنوعی حفظ شود. فقط صدای بزرگسال مجاز است؛ صدای کودک یا تقلید از شخص نام‌برده درخواست نشود.

**وضعیت هیکتور:** افزودن این مستند به‌تنهایی ساخت صدای سفارشی، دریافت شناسهٔ صدا، فعال‌سازی GPT-Live یا پخش صوت در برنامهٔ اندروید را انجام نمی‌دهد. اپ فعلی نباید مدعی پشتیبانی از صدای زنده یا صدای سفارشی شود مگر اینکه مسیر نشست GPT-Live، مجوزها، مدیریت امن کلید و پخش/دریافت صدا واقعاً پیاده‌سازی و آزمایش شده باشند.

مرجع رسمی: [Custom voices](https://developers.openai.com/api/docs/guides/custom-voices)، [GPT-Live](https://developers.openai.com/api/docs/guides/live)، [Text to Speech](https://developers.openai.com/api/reference/resources/audio/subresources/speech/methods/create).


## ۱۳) بهینه‌سازی هزینهٔ مکالمهٔ صوتی و Realtime

نکات اصلی مستندات ارسالی برای طراحی هیکتور:

- هزینهٔ GPT-Live بر اساس مدت نشست فعال محاسبه می‌شود؛ زمان سکوت و انتظار ابزار نیز تا زمان بستن نشست حساب می‌شود. پس از پایان مکالمه، نشست را درست ببندید و مقدار نهایی مصرف را از رویداد بسته‌شدن ثبت کنید.
- برای ساخت نشست WebRTC، ۱۵ ثانیه هزینهٔ آغاز نشست ثبت می‌شود و طبق مستندات در مدت نشست جاری محاسبه/اعتبارسنجی می‌شود؛ آن را دوباره به مدت واقعی اضافه نکنید. ایجاد نشست‌های زودهنگام یا اتصال مجدد بی‌مورد می‌تواند هزینه را بالا ببرد.
- هزینهٔ مدل‌ها و ابزارهای backend جدا از هزینهٔ صوت است. مصرف ورودی/خروجی و توکن‌های cache‌شده را جدا ثبت کنید؛ پاسخ‌های backend را با شناسهٔ یکتا فقط یک بار بشمارید.
- اجرای هم‌زمان ابزارهای مستقل، کوتاه‌کردن زمان انتظار، و بستن نشست صوتی هنگام انجام کارهای طولانی در backend می‌تواند هزینه را کاهش دهد؛ وضعیت کار و نتیجهٔ تأییدشده باید ذخیره شود تا کاربر بعداً ادامه دهد.
- در Realtime، هر پاسخ ممکن است تاریخچهٔ مکالمه را دوباره وارد ورودی کند. برای کنترل هزینه، از محدودیت توکن، خلاصه‌سازی تاریخچه و حذف بخش‌های غیرضروری استفاده کنید؛ تغییرات تاریخچه ممکن است میزان prompt caching را کاهش دهد و حافظهٔ مکالمه را محدود کند.
- هزینه را بر اساس **هزینه به‌ازای کار موفق** بسنجید، نه فقط نرخ مدل؛ شکست‌ها، تکرارها، زمان تکمیل و کیفیت نتیجه را هم حساب کنید.

نمونهٔ مصرف:
```json
{
  "type": "session.usage.updated",
  "event_id": "event_usage_1",
  "usage": { "seconds": 12 },
  "context_window": { "usage_ratio": 0.42 }
}
```
هر به‌روزرسانی، snapshot تجمعیِ مدت مصرف است و نباید با snapshotهای قبلی جمع شود. هنگام بسته‌شدن نشست، مقدار نهایی را یک‌بار ثبت کنید.

**وضعیت هیکتور:** این بخش یک راهنمای طراحی است، نه اندازه‌گیری مصرف واقعی. تا زمانی که مسیر GPT-Live/Realtime پیاده‌سازی نشده باشد، هیکتور نمی‌تواند ادعا کند هزینهٔ صوتی یا مصرف نشست را ثبت یا بهینه می‌کند. کلید API باید روی backend امن بماند و قفل عملیات حساس و توقف اضطراری `HIC-STOP-01` حفظ شوند.

## الزامات ادغام در هیکتور

- کلید API را داخل مخزن، گزارش‌ها یا APK قرار ندهید؛ برای استفاده واقعی، کلید باید در سرویس پشتیبان امن نگهداری شود.
- درخواست تصویر فقط با اقدام و رضایت کاربر ارسال شود؛ تصویر و سابقه مکالمه به‌صورت ناخواسته ارسال نشوند.
- نتیجه تشخیص تصویر صرفاً اطلاعات کمکی است و مجوز عملیات حساس محسوب نمی‌شود.
- قفل عملیات حساس باید حفظ شود و هرگز با پاسخ مدل دور زده نشود.
- کلید توقف اضطراری HIC-STOP-01 باید حفظ شود.
- اتصال واقعی API و اجرای روی دستگاه باید جداگانه آزمایش شوند؛ ثبت این نمونه در مخزن به‌معنای تکمیل اتصال یا ساخت APK جدید نیست.

## ۱۴) الگوی دستور GPT-Live برای هیکتور

این الگو برای نشست صوتی پیوسته طراحی شده است؛ با افزودن متن به برنامهٔ فعلی، قابلیت GPT-Live خودبه‌خود فعال نمی‌شود. برنامه باید واقعاً نشست زنده، ورودی/خروجی صوتی، قطع‌کردن گفتار و واگذاری کار به backend را پیاده‌سازی کند.

```text
You are Hector, a calm, friendly Persian voice assistant.
Speak Persian unless the user asks to switch languages. Speak warmly and naturally at an unhurried pace. Be clear, direct, and concise. Explain one step at a time. If the user is frustrated, acknowledge it briefly and focus on the next useful step.

Backchannel policy: Use moderate, brief acknowledgments. Do not compete with the user's speech.

Interruption policy: Stop speaking when the user interrupts and listen. Distinguish a request to stop speaking from a request to cancel an ongoing task; send task changes or cancellations to the backend.

Delegation policy:
Backend tools:
- Hector chat and saved-context lookup: answer using current, authorized app context.
- Repository/build status: report only verified results returned by the backend.
- Sensitive actions: the app's authorization layer only; the language model cannot grant permission or bypass security controls.

Delegate to the backend when:
- The request requires a fresh lookup, repository/build work, or careful multi-step reasoning.
- The user corrects or changes a task already in progress.
- A tool result is required to state that an action succeeded.

Do not delegate to the backend when:
- A brief conversational reply or clarification is sufficient.
- A still-current, verified result already answers the question.

Delegate before giving an answer that depends on backend work. Do not guess while waiting. Do not claim an action completed until the backend returns a verified result. Ask focused questions when important names, dates, or numbers are unclear. Ignore unrelated background speech and noise.

Security: Never reveal API keys. Never use AI output as authorization for sensitive actions. Preserve the sensitive-operation gate and emergency stop HIC-STOP-01. Do not change security settings or execute actions without the app's explicit authorization and required user confirmation.
```

زبان و تلفظ فارسی باید با آزمایش واقعی صدای انتخاب‌شده بررسی شود؛ متن دستور به‌تنهایی پشتیبانی فارسی را تضمین نمی‌کند. قابلیت‌های backend فهرست‌شده در این الگو فقط زمانی قابل استفاده‌اند که ابزارهای واقعی و مجاز آن‌ها پیاده‌سازی شده باشند.
