# jaudiotagger finds its frame and field classes by name at run time.
-keep class org.jaudiotagger.** { *; }
# ...and names desktop Java (AWT, ImageIO, Swing) that Android does not have and never calls.
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**

# A failed download shows the exception's class name when it has no message.
-keepnames class * extends java.lang.Throwable

# The full flavor's extractor runs JavaScript through Rhino, which builds classes by name,
# and picks its "time ago" patterns by class name per language.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }
-dontwarn org.mozilla.javascript.tools.**
-dontwarn javax.script.**
-dontwarn java.beans.**
-dontwarn org.jspecify.annotations.**
# Rhino's optional fast path for desktop Java; Android has no jdk.dynalink and Rhino runs
# interpreted here.
-dontwarn jdk.dynalink.**
# The build leaves protobuf out of the extractor (see build.gradle.kts), shrunk or not.
-dontwarn com.google.protobuf.**
