CONTIKI = ../../contiki
ifndef TARGET
TARGET=sky
endif




#CFLAGS += "-DSINKADDR=(234<< 8) + 31"  -DROUTE_CONF_ENTRIES=10 -DROUTE_CONF_DEFAULT_LIFETIME=240 
CFLAGS += "-DSINKADDR=(0<< 8) + 1"  -DROUTE_CONF_ENTRIES=10 -DROUTE_CONF_DEFAULT_LIFETIME=240 -DUART1_CONF_TX_WITH_INTERRUPT=1 

# Nullrdc is less efficiant, but gives better results
CFLAGS += -DNETSTACK_CONF_RDC=nullrdc_driver -DUART1_CONF_TX_WITH_INTERRUPT 


all: sink_mesh.sky statprinter.sky statprinter.ihex burn-nodeid.sky burn-nodeid.ihex

sink_mesh.sky: statpacker.o generator.o

statprinter.sky: generator.o

 


include $(CONTIKI)/Makefile.include
