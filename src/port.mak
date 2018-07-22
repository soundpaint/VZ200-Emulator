# -*- Makefile -*-

# globally used paths (assume this file is included as ../port.mak)
CLASSES_DIR	= $(PWD)/../../classes
LIB_DIR		= $(PWD)/../../lib
TGZ_DIR		= $(PWD)/../../tgz

JAVAC	= javac -Xlint:unchecked -Xlint:deprecated
JAR	= jar
