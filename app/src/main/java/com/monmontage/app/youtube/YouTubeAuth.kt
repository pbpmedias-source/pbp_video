package com.monmontage.app.youtube

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Autorisation Google (AuthorizationClient) limitée à :
 *  - youtube.upload : envoyer des vidéos (ni lecture, ni modification, ni suppression) ;
 *  - email : afficher quel compte est connecté.
 * Aucun mot de passe ni jeton longue durée n'est stocké : Google Play services garde l'accord,
 * et l'application redemande un jeton d'une heure à chaque envoi, sans écran si l'accord existe.
 */
object YouTubeAuth {
    private val SCOPES = listOf(
        Scope("https://www.googleapis.com/auth/youtube.upload"),
        Scope("https://www.googleapis.com/auth/userinfo.email"),
    )
    private const val PREFS = "youtube"

    private fun request() = AuthorizationRequest.builder().setRequestedScopes(SCOPES).build()

    /** Résultat brut : peut demander une action de l'utilisateur (pendingIntent). */
    suspend fun authorize(context: Context): AuthorizationResult =
        Identity.getAuthorizationClient(context).authorize(request()).await()

    fun resultFromIntent(context: Context, data: Intent?): AuthorizationResult =
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)

    /** Jeton sans aucun écran ; null si l'utilisateur doit (re)donner son accord. */
    suspend fun silentToken(context: Context): String? {
        val r = authorize(context)
        return if (r.hasResolution()) null else r.accessToken
    }

    /** Jeton refusé par le serveur (expiré) : on le retire du cache pour en obtenir un neuf. */
    suspend fun invalidate(context: Context, token: String) {
        runCatching { Identity.getAuthorizationClient(context).clearToken(ClearTokenRequest.builder().setToken(token).build()).await() }
    }

    suspend fun fetchEmail(token: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("https://www.googleapis.com/oauth2/v3/userinfo").openConnection() as HttpURLConnection
            c.setRequestProperty("Authorization", "Bearer $token")
            c.connectTimeout = 15_000; c.readTimeout = 15_000
            try { JSONObject(c.inputStream.bufferedReader().readText()).optString("email").ifBlank { null } } finally { c.disconnect() }
        }.getOrNull()
    }

    fun connectedEmail(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("email", null)

    fun saveEmail(context: Context, email: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("email", email).apply()
    }

    /** Déconnexion : révoque l'accès de l'application à ce compte, puis l'oublie. */
    suspend fun disconnect(context: Context) {
        val email = connectedEmail(context)
        if (email != null) runCatching {
            Identity.getAuthorizationClient(context).revokeAccess(
                RevokeAccessRequest.builder()
                    .setAccount(android.accounts.Account(email, "com.google"))
                    .setScopes(SCOPES)
                    .build()
            ).await()
        }
        saveEmail(context, null)
    }

    /** Message clair pour les erreurs de configuration les plus fréquentes. */
    fun describe(e: Throwable): String {
        val code = (e as? ApiException)?.statusCode
        return when (code) {
            CommonStatusCodes.DEVELOPER_ERROR ->
                "Configuration Google incomplète : vérifiez l'identifiant Android (nom de paquet et empreinte SHA-1) dans la console Google Cloud."
            CommonStatusCodes.NETWORK_ERROR -> "Pas de connexion Internet."
            CommonStatusCodes.CANCELED -> "Connexion annulée."
            else -> e.message ?: "Connexion impossible."
        }
    }
}
