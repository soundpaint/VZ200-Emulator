include ../common.mak

SRC_DIR		=	$(PWD)/java
PKG		=	emulator/vz200
SRC		=	$(wildcard $(SRC_DIR)/$(PKG)/*.java)
PLAIN_SRC	=	$(subst $(SRC_DIR),.,$(SRC))
OBJ		=	$(subst .java,.class,$(subst \
				$(SRC_DIR),$(BUILD_DIR),$(SRC)))
ROM		=	$(wildcard $(SRC_DIR)/$(PKG)/*.rom)
IMAGES		=	$(wildcard $(SRC_DIR)/$(PKG)/*.png)
ANNOTATIONS_XMLS=	$(wildcard $(SRC_DIR)/$(PKG)/annotations*.xml)
PLAIN_ROM	=	$(subst $(SRC_DIR),.,$(ROM))
TIMESTAMP	=	$(BUILD_DIR)/$(PKG)/TIMESTAMP
JAR_FILE	=	$(JAR_DIR)/vz200.jar
MANIFEST	=	$(PWD)/MANIFEST.MF
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

$(TIMESTAMP) : $(SRC) $(ROM) $(IMAGES) $(ANNOTATIONS_XMLS)
	cd $(SRC_DIR) ; $(JAVAC) -classpath .:$(Z80_JAR) \
		-d $(BUILD_DIR) $(PLAIN_SRC)
	cp -a $(ROM) $(BUILD_DIR)/$(PKG)
	cp -a $(IMAGES) $(BUILD_DIR)/$(PKG)
	cp -a $(ANNOTATIONS_XMLS) $(BUILD_DIR)/$(PKG)
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
	@TGZ_DATE=`date +%Y-%m-%d_%H-%M-%S` ; \
	PROJECT_NAME=VZ200-Emulator ; \
	PROJECT_PATH=`basename \`pwd\`` ; \
	TGZ_PREFIX=$$PROJECT_NAME\_$$TGZ_DATE ; cd .. ; \
	tar cvf $(TARBALL_DIR)/$$TGZ_PREFIX.vz200-src.tar.bz2 \
		--exclude=untracked_files \
		--transform=s/$$PROJECT_PATH/$$TGZ_PREFIX/ \
		--bzip2 $$PROJECT_PATH

#  Local Variables:
#    coding:utf-8
#    mode:Makefile
#  End:
