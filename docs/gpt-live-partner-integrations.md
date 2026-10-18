# راهنمای یکپارچه‌سازی‌های شریک GPT-Live برای هیکتور

> وضعیت: مستندات انتخاب مسیر یکپارچه‌سازی؛ افزودن این فایل اتصال زنده را فعال نمی‌کند.

## انتخاب مسیر

برای برنامه اندرویدی هیکتور که هنوز اتصال صوتی GPT-Live آن اثبات نشده، ابتدا اتصال مستقیم را ارزیابی کنید:
- **WebRTC:** گزینه پیشنهادی برای اتصال صوتی مستقیم، از جمله اتصال آغازشده از سمت سرور.
- **WebSocket:** گزینه جایگزین برای جریان صوت و رویدادهای JSON.
- **SIP/تلفن:** فقط اگر هیکتور واقعاً باید تماس تلفنی ورودی/خروجی را مدیریت کند؛ برای گفت‌وگوی میکروفون داخل گوشی لازم نیست.

یکپارچه‌سازی شریک تنها زمانی انتخاب شود که هیکتور به چارچوب یا سرویس مربوط نیاز داشته باشد:
- **LiveKit:** برای ساخت عامل صوتی با چارچوب LiveKit و افزونه GPT-Live.
- **Twilio:** برای تماس تلفنی ورودی/خروجی از مسیر Agent Connect.
- **Telnyx:** برای تماس خروجی از Voice API.
- **Bandwidth:** برای تماس ورودی از مسیر SIP.
- **Daily/Pipecat:** برای افزودن GPT-Live با سرویس OpenAI Live در Pipecat.

این‌ها گزینه‌های جایگزین‌اند، نه الزام‌هایی که باید همگی نصب شوند. برای نسخه اندرویدی شخصی، از افزودن سرویس تلفنی یا چارچوب اضافی بدون نیاز مشخص خودداری شود.

## چک‌لیست بررسی سازگاری

پیش از انتخاب SDK یا نسخه بسته:
1. مستندات رسمی همان شریک را بررسی کنید و مطمئن شوید نسخه مورد استفاده از شناسه مدل gpt-live-1 پشتیبانی می‌کند.
2. قالب نمونه‌برداری و انتقال صوت، وقفه کاربر، رویدادهای نشست و پخش صوت را بررسی کنید.
3. مشخص کنید delegation از نوع Responses است یا Client و backend فعلی هیکتور چگونه متصل می‌شود.
4. بستن تماس/نشست، لغو کارهای درحال اجرا، reconnect و خطاها را آزمایش کنید.
5. مطمئن شوید مجوزها و قفل عملیات حساس در کد برنامه اجرا می‌شوند، نه فقط در prompt.
6. هیچ کلید محرمانه‌ای در APK یا مخزن ذخیره نشود. برای برنامه موبایل، از backend امن یا اعتبارنامه کوتاه‌عمر استفاده شود.
7. قفل عملیات حساس و توقف اضطراری HIC-STOP-01 حفظ شوند.

## ترتیب پیاده‌سازی پیشنهادی

1. ابتدا یک اتصال مستقیم WebRTC یا WebSocket در محیط آزمایشی انتخاب و نشست را برقرار کنید.
2. صدای ورودی، صدای خروجی، زیرنویس‌ها، وقفه و پایان نشست را جداگانه آزمایش کنید.
3. سپس delegation را به backend متصل کنید و ابزارها را با مجوز و اعتبارسنجی سمت برنامه اجرا کنید.
4. آزمون‌های مجوز، تأیید کاربر، لغو، نتایج قدیمی و جلوگیری از اجرای تکراری را اجرا کنید.
5. تنها پس از موفقیت build و آزمون واقعی روی گوشی، وضعیت را به‌عنوان پیاده‌سازی‌شده ثبت کنید.

## وضعیت فعلی هیکتور

این فایل فقط مسیرهای فنی و چک‌لیست را مستند می‌کند. وجود نام شریک یا لینک راهنما به معنی نصب SDK، ایجاد حساب، برقراری نشست، تنظیم اعتبارنامه یا فعال‌شدن GPT-Live نیست. تا زمانی که کد اتصال و آزمون‌های واقعی انجام نشده‌اند، GPT-Live را فعال اعلام نکنید.

## منابع رسمی

- OpenAI GPT-Live WebRTC: https://developers.openai.com/api/docs/guides/voice-webrtc?api=live
- OpenAI GPT-Live WebSockets: https://developers.openai.com/api/docs/guides/voice-websockets?api=live
- OpenAI GPT-Live SIP: https://developers.openai.com/api/docs/guides/voice-sip?api=live
- LiveKit GPT-Live: https://docs.livekit.io/agents/models/realtime/plugins/gpt-live
- Twilio GPT-Live: https://www.twilio.com/en-us/blog/developers/twilio-openai-gpt-live-1-api-resources
- Telnyx GPT-Live: https://developers.telnyx.com/docs/voice/sip-trunking/gpt-live-configuration-guide
- Bandwidth GPT-Live: https://dev.bandwidth.com/docs/voice/integrations/openai/live/sip/
- Daily/Pipecat GPT-Live: https://docs.pipecat.ai/api-reference/server/services/s2s/openai-live
