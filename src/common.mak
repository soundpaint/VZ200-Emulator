# If multiple JDKs are installed, use variable JDK_BIN to define which
# one to use.  If left empty, the default one as specified by the
# environment (search path, etc.), will be used.
#
# If set, it must end with the path segment separator character
# (i.e. usually, with the slash ("/") character).
#
#JDK_BIN=/usr/lib/jvm/java-8-openjdk-amd64/bin/
JDK_BIN=

# globally used paths (assume this file is included as ../common.mak)
VZ200_EMU_HOME=$(PWD)/../..

# compiled class files root directory
# (run 'make classes' to generate)
BUILD_DIR=$(VZ200_EMU_HOME)/build

# binary target directory; contains archive file with the compiled classes
# (run 'make jar' to generate)
JAR_DIR=$(VZ200_EMU_HOME)/jar

TARBALL_DIR=$(VZ200_EMU_HOME)/../VZ200-Emulator_tarballs

JAVAC=$(JDK_BIN)javac -Xlint:all
JAVA=$(JDK_BIN)java
JAR=$(JDK_BIN)jar

#  Local Variables:
#    coding:utf-8
#    mode:Makefile
#  End:
