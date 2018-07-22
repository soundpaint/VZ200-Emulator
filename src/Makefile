all:
	cd z80 ; make
	cd vz200 ; make

clean:
	cd z80 ; make clean
	cd vz200 ; make clean

distclean:
	cd z80 ; make distclean
	cd vz200 ; make distclean

run: all
	java -cp ../build emulator.vz200.VZ200

tarball:
	cd z80 ; make tarball
	cd vz200 ; make tarball

#  Local Variables:
#    coding:utf-8
#    mode:Makefile
#  End:
