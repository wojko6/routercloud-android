package com.wojko6.routercloud.security

import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.wojko6.routercloud.network.RouterCloudSessionCookie
import javax.crypto.Cipher

class BiometricSessionController(
    private val activity: FragmentActivity,
) {
    private val sessionStore =
        SecureSessionStore(activity.applicationContext)

    private val biometricManager =
        BiometricManager.from(activity)

    private sealed class PendingOperation {
        data class Save(
            val cookies: List<RouterCloudSessionCookie>,
            val onSuccess: () -> Unit,
            val onError: (String?) -> Unit,
        ) : PendingOperation()

        data class Restore(
            val onSuccess: (List<RouterCloudSessionCookie>) -> Unit,
            val onError: (String?) -> Unit,
        ) : PendingOperation()
    }

    private var pendingOperation: PendingOperation? = null

    private val biometricPrompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {

            override fun onAuthenticationSucceeded(
                result: BiometricPrompt.AuthenticationResult,
            ) {
                super.onAuthenticationSucceeded(result)

                val operation = pendingOperation
                    ?: return

                pendingOperation = null

                val cipher = result.cryptoObject?.cipher

                if (cipher == null) {
                    notifyError(
                        operation,
                        "System nie udostępnił obiektu kryptograficznego.",
                    )
                    return
                }

                try {
                    when (operation) {
                        is PendingOperation.Save -> {
                            sessionStore.save(
                                cipher = cipher,
                                cookies = operation.cookies,
                            )
                            operation.onSuccess()
                        }

                        is PendingOperation.Restore -> {
                            val cookies =
                                sessionStore.restore(cipher)

                            operation.onSuccess(cookies)
                        }
                    }
                } catch (e: Exception) {
                    sessionStore.clear()

                    notifyError(
                        operation,
                        e.message
                            ?: "Nie udało się odblokować bezpiecznej sesji.",
                    )
                }
            }

            override fun onAuthenticationError(
                errorCode: Int,
                errString: CharSequence,
            ) {
                super.onAuthenticationError(
                    errorCode,
                    errString,
                )

                val operation = pendingOperation
                    ?: return

                pendingOperation = null

                val message =
                    when (errorCode) {
                        BiometricPrompt.ERROR_CANCELED,
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON ->
                            null

                        BiometricPrompt.ERROR_LOCKOUT ->
                            "Czytnik odcisku palca jest chwilowo zablokowany."

                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                            "Czytnik odcisku palca jest zablokowany. Odblokuj telefon kodem systemowym."

                        else ->
                            errString.toString()
                                .takeIf { it.isNotBlank() }
                                ?: "Uwierzytelnienie odciskiem palca nie powiodło się."
                    }

                notifyError(
                    operation,
                    message,
                )
            }
        },
    )

    fun isStrongBiometricAvailable(): Boolean {
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG,
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun hasSavedSession(): Boolean =
        sessionStore.hasSavedSession()

    fun saveSession(
        cookies: List<RouterCloudSessionCookie>,
        onSuccess: () -> Unit,
        onError: (String?) -> Unit,
    ) {
        if (cookies.isEmpty()) {
            onError(
                "RouterCloud nie zwrócił aktywnej sesji do zapisania."
            )
            return
        }

        if (!isStrongBiometricAvailable()) {
            onError(
                "Silne uwierzytelnianie odciskiem palca nie jest dostępne."
            )
            return
        }

        try {
            val cipher =
                sessionStore.createEncryptionCipher()

            pendingOperation = PendingOperation.Save(
                cookies = cookies,
                onSuccess = onSuccess,
                onError = onError,
            )

            biometricPrompt.authenticate(
                promptInfo(
                    title = "Zabezpiecz RouterCloud",
                    subtitle =
                        "Potwierdź odciskiem palca, aby zapisać bezpieczną sesję.",
                ),
                BiometricPrompt.CryptoObject(cipher),
            )
        } catch (e: KeyPermanentlyInvalidatedException) {
            sessionStore.resetKeyAndSession()

            onError(
                "Klucz bezpieczeństwa został unieważniony. Zaloguj się ponownie."
            )
        } catch (e: Exception) {
            onError(
                e.message
                    ?: "Nie udało się przygotować zabezpieczenia biometrycznego."
            )
        }
    }

    fun restoreSession(
        onSuccess: (List<RouterCloudSessionCookie>) -> Unit,
        onError: (String?) -> Unit,
    ) {
        if (!hasSavedSession()) {
            onError(
                "Brak zapisanej sesji RouterCloud."
            )
            return
        }

        if (!isStrongBiometricAvailable()) {
            onError(
                "Odcisk palca nie jest dostępny."
            )
            return
        }

        try {
            val cipher =
                sessionStore.createDecryptionCipher()

            pendingOperation =
                PendingOperation.Restore(
                    onSuccess = onSuccess,
                    onError = onError,
                )

            biometricPrompt.authenticate(
                promptInfo(
                    title = "Odblokuj RouterCloud",
                    subtitle =
                        "Użyj odcisku palca, aby przywrócić sesję.",
                ),
                BiometricPrompt.CryptoObject(cipher),
            )
        } catch (e: KeyPermanentlyInvalidatedException) {
            sessionStore.resetKeyAndSession()

            onError(
                "Zmieniono zapisane dane biometryczne. Zaloguj się ponownie."
            )
        } catch (e: Exception) {
            sessionStore.clear()

            onError(
                e.message
                    ?: "Nie udało się odczytać zabezpieczonej sesji."
            )
        }
    }

    fun clearSavedSession() {
        sessionStore.clear()
    }

    private fun promptInfo(
        title: String,
        subtitle: String,
    ): BiometricPrompt.PromptInfo {
        return BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG,
            )
            .setNegativeButtonText("Anuluj")
            .build()
    }

    private fun notifyError(
        operation: PendingOperation,
        message: String?,
    ) {
        when (operation) {
            is PendingOperation.Save ->
                operation.onError(message)

            is PendingOperation.Restore ->
                operation.onError(message)
        }
    }
}
