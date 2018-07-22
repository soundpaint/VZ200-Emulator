include ../port.mak

SRC_DIR		=	$(PWD)/java
PKG		=	emulator/vz200
SRC		=	$(wildcard $(SRC_DIR)/$(PKG)/*.java)
PLAIN_SRC	=	$(subst $(SRC_DIR),.,$(SRC))
OBJ		=	$(subst .java,.class,$(subst \
				$(SRC_DIR),$(CLASSES_DIR),$(SRC)))
ROM		=	$(wildcard $(SRC_DIR)/$(PKG)/*.rom)
PLAIN_ROM	=	$(subst $(SRC_DIR),.,$(ROM))
TIMESTAMP	=	$(CLASSES_DIR)/$(PKG)/TIMESTAMP
JAR_FILE	=	$(LIB_DIR)/vz200.jar
MANIFEST	=	$(PWD)/MANIFEST.MF
SRC_TGZ		=	$(TGZ_DIR)/vz200-src.tgz
Z80_JAR		=	../../../lib/z80.jar

all: $(JAR_FILE)

$(TIMESTAMP) : $(SRC) $(ROM)
	cd $(SRC_DIR) ; $(JAVAC) -classpath .:$(Z80_JAR) \
		-d $(CLASSES_DIR) $(PLAIN_SRC)
	cp -a $(ROM) $(CLASSES_DIR)/$(PKG)
	touch $@

$(JAR_FILE) : $(TIMESTAMP)
	cd $(CLASSES_DIR) ; $(JAR) cvfm $@ $(MANIFEST) \
		$(PKG)

objclean:
	rm -rf $(CLASSES_DIR)/$(PKG)

libclean:
	rm -f $(JAR_FILE)

bkpclean:
	find . \( -name \*~ -or -name \*.bak \) -exec rm \{} \;

clean: objclean libclean

veryclean: clean bkpclean

tgz: bkpclean
	cd .. ; tar -cvf - vz200 | gzip --best > $(SRC_TGZ)
