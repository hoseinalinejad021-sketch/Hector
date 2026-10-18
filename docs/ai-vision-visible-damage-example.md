# نمونه تشخیص آسیب محصول برای هیکتور

این فایل دو قطعه ارسالی را به‌عنوان نمونه آزمون ثبت می‌کند. این نمونه به‌تنهایی قابلیت هوش مصنوعی را به APK وصل نمی‌کند.

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

## الزامات ادغام در هیکتور

- کلید API را داخل مخزن، گزارش‌ها یا APK قرار ندهید؛ برای استفاده واقعی، کلید باید در سرویس پشتیبان امن نگهداری شود.
- درخواست تصویر فقط با اقدام و رضایت کاربر ارسال شود؛ تصویر و سابقه مکالمه به‌صورت ناخواسته ارسال نشوند.
- نتیجه تشخیص تصویر صرفاً اطلاعات کمکی است و مجوز عملیات حساس محسوب نمی‌شود.
- قفل عملیات حساس باید حفظ شود و هرگز با پاسخ مدل دور زده نشود.
- کلید توقف اضطراری HIC-STOP-01 باید حفظ شود.
- اتصال واقعی API و اجرای روی دستگاه باید جداگانه آزمایش شوند؛ ثبت این نمونه در مخزن به‌معنای تکمیل اتصال یا ساخت APK جدید نیست.