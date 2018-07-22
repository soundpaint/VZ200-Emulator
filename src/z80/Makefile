include ../common.mak

SRC_DIR		=	$(PWD)/java
PKG		=	emulator/z80
SRC		=	$(wildcard $(SRC_DIR)/$(PKG)/*.java)
PLAIN_SRC	=	$(subst $(SRC_DIR),.,$(SRC))
OBJ		=	$(subst .java,.class,$(subst \
				$(SRC_DIR),$(BUILD_DIR),$(SRC)))
TIMESTAMP	=	$(BUILD_DIR)/$(PKG)/TIMESTAMP
JAR_FILE	=	$(JAR_DIR)/z80.jar
MANIFEST	=	$(PWD)/MANIFEST.MF
SRC_TARBALL	=	$(TARBALL_DIR)/z80-src.bz2

all: $(JAR_FILE)

# compiled class files root directory
# (run 'make classes' to generate)

$(BUILD_DIR):
	mkdir -p $@

$(JAR_DIR):
	mkdir -p $@

$(TARBALL_DIR):
	mkdir -p $@

$(TIMESTAMP) : $(SRC)
	cd $(SRC_DIR) ; $(JAVAC) -d $(BUILD_DIR) $(PLAIN_SRC)
	touch $@

$(JAR_FILE) : $(JAR_DIR) $(TIMESTAMP)
	cd $(BUILD_DIR) ; $(JAR) cvfm $@ $(MANIFEST) \
		$(PKG)

objclean:
	rm -rf $(BUILD_DIR)/$(PKG)

libclean:
	rm -f $(JAR_FILE)

bkpclean:
	find . \( -name \*~ -or -name \*.bak \) -exec rm \{} \;

clean: objclean libclean

distclean: clean bkpclean

tarball: distclean $(TARBALL_DIR)
	cd .. ; tar -cvf - z80 | bzip2 --best > $(SRC_TARBALL)

#  Local Variables:
#    coding:utf-8
#    mode:Makefile
#  End:
