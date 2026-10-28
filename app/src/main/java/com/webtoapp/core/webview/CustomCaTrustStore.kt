package com.webtoapp.core.webview

import android.content.Context
import com.webtoapp.core.logging.AppLogger
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager





















object CustomCaTrustStore {
    private const val TAG = "CustomCaTrustStore"
    private const val ASSET_DIR = "wta_custom_ca"

    @Volatile private var validator: CustomCaValidator? = null
    @Volatile private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        val anchors = loadFromAssets(context)
        validator = CustomCaValidator(anchors)
        initialized = true
        if (anchors.isNotEmpty()) {
            AppLogger.i(TAG, "Loaded ${anchors.size} custom CA anchor(s) from assets/$ASSET_DIR")
        }
    }

    
    @Synchronized
    fun initFromDer(certBytes: List<ByteArray>) {
        val factory = CertificateFactory.getInstance("X.509")
        val anchors = certBytes.mapNotNull { bytes ->
            runCatching {
                factory.generateCertificates(bytes.inputStream())
                    .filterIsInstance<X509Certificate>()
            }.getOrDefault(emptyList())
        }.flatten()
        validator = CustomCaValidator(anchors)
        initialized = true
    }

    @Synchronized
    fun reset() {
        validator = null
        initialized = false
    }

    fun hasAnchors(): Boolean = validator?.hasAnchors() == true

    
    fun getAnchorCertificates(): List<X509Certificate> = validator?.getAnchorCertificates() ?: emptyList()

    
    fun isServerCertTrusted(leaf: X509Certificate): Boolean =
        validator?.isServerCertTrusted(leaf) == true

    private fun loadFromAssets(context: Context): List<X509Certificate> {
        val names = runCatching { context.assets.list(ASSET_DIR) }.getOrNull()
        if (names.isNullOrEmpty()) return emptyList()
        val factory = CertificateFactory.getInstance("X.509")
        val out = mutableListOf<X509Certificate>()
        names.sorted().forEach { name ->
            runCatching {
                context.assets.open("$ASSET_DIR/$name").use { input ->
                    out.addAll(
                        factory.generateCertificates(input).filterIsInstance<X509Certificate>()
                    )
                }
            }.onFailure { e ->
                AppLogger.w(TAG, "Failed to load custom CA asset $name: ${e.message}")
            }
        }
        return out
    }
}






class CustomCaValidator(private val anchors: List<X509Certificate>) {
    private val trustManager: X509TrustManager? = buildTrustManager()

    fun hasAnchors(): Boolean = anchors.isNotEmpty()

    fun getAnchorCertificates(): List<X509Certificate> = anchors.toList()

    fun isServerCertTrusted(leaf: X509Certificate): Boolean {
        if (anchors.isEmpty()) return false

        
        
        val tm = trustManager
        if (tm != null) {
            runCatching {
                tm.checkServerTrusted(arrayOf(leaf), "ECDHE_RSA")
                return true
            }
        }

        
        
        
        val issuer = leaf.issuerX500Principal
        return anchors.any { anchor ->
            anchor.subjectX500Principal == issuer &&
                runCatching { leaf.verify(anchor.publicKey); true }.getOrDefault(false)
        }
    }

    private fun buildTrustManager(): X509TrustManager? = runCatching {
        if (anchors.isEmpty()) return null
        val ks = KeyStore.getInstance(KeyStore.getDefaultType())
        ks.load(null, null)
        anchors.forEachIndexed { i, cert -> ks.setCertificateEntry("wta_custom_ca_$i", cert) }
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(ks)
        tmf.trustManagers.firstOrNull { it is X509TrustManager } as? X509TrustManager
    }.onFailure { e ->
        AppLogger.w("CustomCaValidator", "TrustManager build failed: ${e.message}")
    }.getOrNull()
}
