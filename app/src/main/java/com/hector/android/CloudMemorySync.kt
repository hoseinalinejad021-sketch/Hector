package com.hector.android

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.security.MessageDigest

data class CloudSyncResult(
    val memory: String,
    val history: String,
    val updatedAtMillis: Long,
    val remoteWon: Boolean
)

/**
 * Optional, account-scoped cloud sync. Only memory and conversation history are synced.
 * The AI API key, endpoint, and model settings are deliberately excluded.
 *
 * Firebase must be configured for applicationId com.hector.android and Firestore rules
 * must be deployed before this class can access cloud data.
 */
object CloudMemorySync {

    fun isConfigured(context: Context): Boolean = firebaseApp(context) != null

    fun authenticate(
        context: Context,
        email: String,
        password: String,
        createAccount: Boolean,
        callback: (String?) -> Unit
    ) {
        if (email.isBlank() || password.length < 6) {
            callback("ایمیل معتبر و رمز عبور حداقل ۶ کاراکتری لازم است.")
            return
        }
        val app = firebaseApp(context)
        if (app == null) {
            callback("پیکربندی Firebase موجود نیست؛ فایل app/google-services.json را اضافه کن.")
            return
        }
        val auth = FirebaseAuth.getInstance(app)
        val task = if (createAccount) auth.createUserWithEmailAndPassword(email, password)
                   else auth.signInWithEmailAndPassword(email, password)
        task.addOnCompleteListener { result ->
            callback(if (result.isSuccessful) null else result.exception?.localizedMessage ?: "ورود ناموفق بود.")
        }
    }

    fun sync(
        context: Context,
        localMemory: String,
        localHistory: String,
        localUpdatedAtMillis: Long,
        callback: (Result<CloudSyncResult>) -> Unit
    ) {
        val app = firebaseApp(context)
        if (app == null) {
            callback(Result.failure(IllegalStateException("Firebase پیکربندی نشده است.")))
            return
        }
        val user = FirebaseAuth.getInstance(app).currentUser
        if (user == null) {
            callback(Result.failure(IllegalStateException("برای همگام‌سازی وارد حساب هیکتور شو.")))
            return
        }
        // Validate against the same per-field limits enforced by firestore.rules.
        if (!CloudSyncPayloadLimits.isWithinLimits(localMemory, localHistory)) {
            callback(Result.failure(IllegalStateException(
                "حافظه یا تاریخچه از حد مجاز همگام‌سازی ابری بزرگ‌تر است؛ داده‌ها را کوتاه کن و دوباره تلاش کن."
            )))
            return
        }
        val document = FirebaseFirestore.getInstance(app)
            .collection("users").document(user.uid)
            .collection("hector").document("memory")

        document.get()
            .addOnSuccessListener { snapshot ->
                val remoteTime = snapshot.getLong("updatedAtMillis") ?: 0L
                if (snapshot.exists() && remoteTime > localUpdatedAtMillis) {
                    callback(Result.success(CloudSyncResult(
                        memory = snapshot.getString("memory").orEmpty(),
                        history = snapshot.getString("history").orEmpty(),
                        updatedAtMillis = remoteTime,
                        remoteWon = true
                    )))
                } else {
                    val now = maxOf(System.currentTimeMillis(), localUpdatedAtMillis)
                    val payload = hashMapOf<String, Any>(
                        "schemaVersion" to 1,
                        "memory" to localMemory,
                        "history" to localHistory,
                        "updatedAtMillis" to now
                    )
                    document.set(payload)
                        .addOnSuccessListener {
                            callback(Result.success(CloudSyncResult(localMemory, localHistory, now, false)))
                        }
                        .addOnFailureListener { error -> callback(Result.failure(error)) }
                }
            }
            .addOnFailureListener { error -> callback(Result.failure(error)) }
    }

    fun registerDeviceToken(context: Context, token: String, callback: (String?) -> Unit) {
        val app = firebaseApp(context)
        val user = app?.let { FirebaseAuth.getInstance(it).currentUser }
        if (app == null || user == null) {
            callback("پیکربندی Firebase یا ورود به حساب موجود نیست.")
            return
        }
        val tokenId = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        FirebaseFirestore.getInstance(app)
            .collection("users").document(user.uid)
            .collection("devices").document(tokenId)
            .set(mapOf("token" to token, "updatedAtMillis" to System.currentTimeMillis()))
            .addOnSuccessListener { callback(null) }
            .addOnFailureListener { callback(it.localizedMessage ?: "ثبت دستگاه ناموفق بود.") }
    }

    private fun firebaseApp(context: Context): FirebaseApp? {
        return try {
            FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context)
        } catch (_: Exception) {
            null
        }
    }
}
