# Hector AI gateway (Cloudflare Worker)

This Worker keeps the OpenAI API key on the server rather than embedding it in the Android APK.

## Deploy from a phone

1. Sign in to Cloudflare and open Workers & Pages.
2. Create a Worker named `hector-ai-gateway`.
3. Paste the contents of `src/index.js` into the Worker editor and deploy it.
4. In Worker Settings → Variables and Secrets, add a secret named `OPENAI_API_KEY` and paste your key there. Never put the key in GitHub or the APK.
5. Optional: set a variable `OPENAI_MODEL` to a model available to your API account. The default here is `gpt-4.1-mini`.
6. Test with a POST request to `https://YOUR-WORKER.workers.dev/chat` with JSON body `{"message":"سلام، خودت را معرفی کن" }`.

Expected success response: `{"answer":"...","model":"..."}`.

## Important

- This endpoint is a starter backend, not yet connected to the Android UI.
- Before production use, add user authentication and rate limiting so strangers cannot run up API charges.
- Do not paste your OpenAI key into chat, source files, GitHub commits, or the APK.
- API usage may incur separate charges.
