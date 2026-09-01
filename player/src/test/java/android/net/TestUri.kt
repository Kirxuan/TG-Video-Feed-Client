package android.net

import android.os.Parcel
/**
 * JVM-only Uri value used because the Android SDK stub's static Uri.parse()
 * intentionally throws outside an Android runtime.
 */
internal class TestUri(
    private val scheme: String? = null,
    private val authority: String? = null,
    private val pathSegments: List<String> = emptyList(),
    private val rawValue: String = "",
) : Uri() {
    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) = Unit
    override fun buildUpon(): Uri.Builder = throw UnsupportedOperationException()
    override fun getAuthority(): String? = authority
    override fun getEncodedAuthority(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getEncodedPath(): String? = null
    override fun getEncodedQuery(): String? = null
    override fun getEncodedSchemeSpecificPart(): String? = null
    override fun getEncodedUserInfo(): String? = null
    override fun getFragment(): String? = null
    override fun getHost(): String? = null
    override fun getLastPathSegment(): String? = null
    override fun getPath(): String? = null
    override fun getPathSegments(): List<String> = pathSegments
    override fun getPort(): Int = -1
    override fun getQuery(): String? = null
    override fun getScheme(): String? = scheme
    override fun getSchemeSpecificPart(): String? = null
    override fun getUserInfo(): String? = null
    override fun isHierarchical(): Boolean = true
    override fun isRelative(): Boolean = true
    override fun toString(): String = rawValue
}
