include ../port.mak

SRC_DIR		=	$(PWD)/java
PKG		=	emulator/z80
SRC		=	$(wildcard $(SRC_DIR)/$(PKG)/*.java)
PLAIN_SRC	=	$(subst $(SRC_DIR),.,$(SRC))
OBJ		=	$(subst .java,.class,$(subst \
				$(SRC_DIR),$(CLASSES_DIR),$(SRC)))
TIMESTAMP	=	$(CLASSES_DIR)/$(PKG)/TIMESTAMP
JAR_FILE	=	$(LIB_DIR)/z80.jar
MANIFEST	=	$(PWD)/MANIFEST.MF
SRC_TGZ		=	$(TGZ_DIR)/z80-src.tgz

all: $(JAR_FILE)

$(TIMESTAMP) : $(SRC)
	cd $(SRC_DIR) ; $(JAVAC) -d $(CLASSES_DIR) $(PLAIN_SRC)
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
	cd .. ; tar -cvf - z80 | gzip --best > $(SRC_TGZ)
