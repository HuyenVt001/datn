# ==================================================================
# ProGuard/R8 rules — Snapget
# ==================================================================
# Bat dau co hieu luc tu 2026-07-28 (truoc do isMinifyEnabled=false nen
# file nay hoan toan vo tac dung). Xem SECURITY.md muc 8.4.
#
# Nguyen tac: thu vien lon (Firebase, Retrofit, OkHttp, Media3, CameraX,
# WorkManager, Hilt) deu ship "consumer rules" trong AAR cua chinh no nen
# KHONG can khai bao lai o day. Chi khai bao thu R8 KHONG the tu suy ra:
# chu yeu la cac class bi truy cap bang REFLECTION (Gson).
# ==================================================================


# ------------------------------------------------------------------
# 1. Giu metadata can cho reflection / generic
# ------------------------------------------------------------------
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes Exceptions
-keepattributes AnnotationDefault
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeVisibleTypeAnnotations

# Giu so dong de doc duoc stack trace tu crash report,
# nhung DAU ten file goc (khong lam lo cau truc source).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile


# ------------------------------------------------------------------
# 2. Gson — QUAN TRONG NHAT
# ------------------------------------------------------------------
# Gson map JSON <-> object bang TEN FIELD qua reflection. R8 doi ten field
# => parse ra null het ma KHONG bao loi luc build, chi vo luc chay.
# => phai giu nguyen ten field cua toan bo DTO va model.

-keep class com.example.snapget.core.network.dto.** { *; }
-keep class com.example.snapget.core.model.** { *; }

# Enum trong DTO (contentType PHOTO/VIDEO, messageType TEXT/VOICE/...)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# TypeToken cua Gson (generic bi xoa neu khong giu)
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Field khong bao gio duoc gan trong code Kotlin (Gson gan bang reflection)
-keepclassmembers class com.example.snapget.** {
    <init>();
}


# ------------------------------------------------------------------
# 3. Retrofit + OkHttp
# ------------------------------------------------------------------
# Retrofit doc annotation tren interface API bang reflection.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

-keep interface com.example.snapget.core.network.api.** { *; }

-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**


# ------------------------------------------------------------------
# 4. Kotlin
# ------------------------------------------------------------------
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-dontwarn kotlin.Unit
-dontwarn kotlinx.coroutines.**


# ------------------------------------------------------------------
# 5. XOA LOG KHOI BAN RELEASE  <-- muc dich bao mat
# ------------------------------------------------------------------
# Truoc day AuthRepository log ca EMAIL user khi dang nhap/dang ky/reset
# mat khau. R8 tat => log do nam nguyen trong APK release, doc duoc bang
# logcat tren may root. Nay xoa han o buoc optimize.
# Giu lai w() va e() de con chan doan duoc su co (da bo PII khoi chung).
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** wtf(...);
}
-assumenosideeffects class java.io.PrintStream {
    public void println(%);
    public void println(**);
    public void print(%);
    public void print(**);
}


# ------------------------------------------------------------------
# 6. Khac
# ------------------------------------------------------------------
# Glance/WorkManager khoi tao Worker bang reflection
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

# zxing (sinh anh QR)
-dontwarn com.google.zxing.**

# Khong co WebView + khong co JavascriptInterface trong app nay
# (da xac nhan: grep WebView -> 0 ket qua) nen khong can rule tuong ung.
