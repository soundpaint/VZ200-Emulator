include ../common.mak

SRC_DIR		=	$(PWD)/java
PKG		=	emulator/vz200
SRC		=	$(wildcard $(SRC_DIR)/$(PKG)/*.java)
PLAIN_SRC	=	$(subst $(SRC_DIR),.,$(SRC))
OBJ		=	$(subst .java,.class,$(subst \
				$(SRC_DIR),$(BUILD_DIR),$(SRC)))
ROM		=	$(wildcard $(SRC_DIR)/$(PKG)/*.rom)
IMAGES		=	$(wildcard $(SRC_DIR)/$(PKG)/*.png)
ANNOTATIONS_XML	=	$(wildcard $(SRC_DIR)/$(PKG)/annotations.xml)
PLAIN_ROM	=	$(subst $(SRC_DIR),.,$(ROM))
TIMESTAMP	=	$(BUILD_DIR)/$(PKG)/TIMESTAMP
JAR_FILE	=	$(JAR_DIR)/vz200.jar
MANIFEST	=	$(PWD)/MANIFEST.MF
SRC_TARBALL	=	$(TARBALL_DIR)/vz200-src.bz2
Z80_JAR		=	$(JAR_DIR)/z80.jar

all: $(JAR_FILE)

# compiled class files root directory
# (run 'make classes' to generate)

$(BUILD_DIR):
	mkdir -p $@

$(JAR_DIR):
	mkdir -p $@

$(TARBALL_DIR):
	mkdir -p $@

$(TIMESTAMP) : $(SRC) $(ROM) $(IMAGES) $(ANNOTATIONS_XML)
	cd $(SRC_DIR) ; $(JAVAC) -classpath .:$(Z80_JAR) \
		-d $(BUILD_DIR) $(PLAIN_SRC)
	cp -a $(ROM) $(BUILD_DIR)/$(PKG)
	cp -a $(IMAGES) $(BUILD_DIR)/$(PKG)
	cp -a $(ANNOTATIONS_XML) $(BUILD_DIR)/$(PKG)
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
	cd .. ; tar -cvf - vz200 | bzip2 --best > $(SRC_TARBALL)

#  Local Variables:
#    coding:utf-8
#    mode:Makefile
#  End:
