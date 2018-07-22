# globally used paths (assume this file is included as ../common.mak)
VZ200_EMU_HOME = $(PWD)/../..

# compiled class files root directory
# (run 'make classes' to generate)
BUILD_DIR = $(VZ200_EMU_HOME)/build

# binary target directory; contains archive file with the compiled classes
# (run 'make jar' to generate)
JAR_DIR = $(VZ200_EMU_HOME)/jar

TARBALL_DIR = $(VZ200_EMU_HOME)/tarball

JAVAC = javac -Xlint:all
JAR = jar

#  Local Variables:
#    coding:utf-8
#    mode:Makefile
#  End:
