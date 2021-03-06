# src/bin/pgbench/Makefile

PGFILEDESC = "ysql_bench - a simple program for running benchmark tests"
PGAPPICON = win32

subdir = src/bin/pgbench
top_builddir = ../../..
include $(top_builddir)/src/Makefile.global

OBJS = pgbench.o exprparse.o $(WIN32RES)

override CPPFLAGS := -I. -I$(srcdir) -I$(libpq_srcdir) $(CPPFLAGS)
LDFLAGS_INTERNAL += -L$(top_builddir)/src/fe_utils -lpgfeutils $(libpq_pgport)

ifneq ($(PORTNAME), win32)
override CFLAGS += $(PTHREAD_CFLAGS)
endif
LIBS += $(PTHREAD_LIBS)


all: ysql_bench

ysql_bench: $(OBJS) | submake-libpq submake-libpgport submake-libpgfeutils
	$(CC) $(CFLAGS) $^ $(LDFLAGS) $(LDFLAGS_EX) $(LIBS) -o $@$(X)

# exprscan is compiled as part of exprparse
exprparse.o: exprscan.c

distprep: exprparse.c exprscan.c

install: all installdirs
	$(INSTALL_PROGRAM) ysql_bench$(X) '$(DESTDIR)$(bindir)/ysql_bench$(X)'

installdirs:
	$(MKDIR_P) '$(DESTDIR)$(bindir)'

uninstall:
	rm -f '$(DESTDIR)$(bindir)/ysql_bench$(X)'

clean distclean:
	rm -f ysql_bench$(X) $(OBJS)
	rm -rf tmp_check

maintainer-clean: distclean
	rm -f exprparse.c exprscan.c

check:
	$(prove_check)

installcheck:
	$(prove_installcheck)
