# Release R8 rules for NullKey AI. Debug builds do not minify.
#
# Applied only when release minify is on (the default). Opt out with
# -Pnullkey.releaseMinify=false or NULLKEY_RELEASE_MINIFY=false.
# Upload app/build/outputs/mapping/release/mapping.txt to Play with the AAB.
# Do not commit mapping files or keystores.

# Deobfuscation stack traces. Play Console consumes mapping.txt.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod,Exceptions

# ---------------------------------------------------------------------------
# IME / manifest components
# The system instantiates these by class name. AGP also keeps manifest
# components; these rules document the IME contract. xml/method.xml names
# MainActivity as the IME settings activity, and keyboard.xml names the
# custom view, so those class names must survive shrinking.
# ---------------------------------------------------------------------------
-keep class com.nullverse.nullkeyai.ime.NullKeyImeService { <init>(); }
-keep class com.nullverse.nullkeyai.clipboard.ClipboardMonitorService { <init>(); }
-keep class com.nullverse.nullkeyai.ui.MainActivity { <init>(); }
-keep class com.nullverse.nullkeyai.ui.ClipDetailActivity { <init>(); }
-keep class com.nullverse.nullkeyai.ui.TrashActivity { <init>(); }
-keep class com.nullverse.nullkeyai.diagnostics.ClipboardLabActivity { <init>(); }
-keep class com.nullverse.nullkeyai.ime.engine.NullKeyKeyboardView {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ---------------------------------------------------------------------------
# Room
# Room loads <database simple name>_Impl via Class.forName. Both the
# database class and the generated impl must keep those exact names.
# Entities and DAO interfaces are kept so constructor/field shrinking cannot
# break the generated SQLite adapters. Library consumer rules also apply.
# ---------------------------------------------------------------------------
-keep class com.nullverse.nullkeyai.db.NullKeyDatabase { <init>(); }
-keep class com.nullverse.nullkeyai.db.NullKeyDatabase_Impl { <init>(); }
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class * extends androidx.room.migration.Migration { *; }
-dontwarn androidx.room.paging.**

# ---------------------------------------------------------------------------
# Persisted enum names
# Room TEXT columns, SharedPreferences, and JSON backups store Enum.name
# (TEXT, DARK_VAULT, PIN, TOMBSTONE, ...). SQL defaults such as 'TEXT' are
# literals. Renaming constants would desync existing vaults and prefs.
# ---------------------------------------------------------------------------
-keep enum com.nullverse.nullkeyai.** { *; }

# Bundled ML Kit Latin OCR. The AAR consumer rules keep proto fields and
# native method names for libmlkit_google_ocr_pipeline.so. Clearcut
# (transport-backend-cct) is excluded in Gradle; ignore optional refs.
-dontwarn com.google.android.datatransport.cct.**
