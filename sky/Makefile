CONTIKI = ../../contiki
ifndef TARGET
TARGET=sky
endif


PROJECT_SOURCEFILES=generator.c

#CFLAGS += "-DSINKADDR=(234<< 8) + 31"  -DROUTE_CONF_ENTRIES=10 -DROUTE_CONF_DEFAULT_LIFETIME=240 
CFLAGS += "-DSINKADDR=(0<< 8) + 1"  -DROUTE_CONF_ENTRIES=10 -DROUTE_CONF_DEFAULT_LIFETIME=240 -DUART1_CONF_TX_WITH_INTERRUPT=1 

# Nullrdc is less efficiant, but gives better results
CFLAGS += -DNETSTACK_CONF_RDC=nullrdc_driver


all: sink_mesh.sky statprinter.sky

sink_mesh.sky: statpacker.o


include $(CONTIKI)/Makefile.include
