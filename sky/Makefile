CONTIKI = ../../contiki
ifndef TARGET
TARGET=sky
endif

SINKADD="(0<< 8) + 20"


#CFLAGS += "-DSINKADDR=(234<< 8) + 31"  -DROUTE_CONF_ENTRIES=10 -DROUTE_CONF_DEFAULT_LIFETIME=240 
CFLAGS += -DSINKADDR=$(SINKADD)  -DROUTE_CONF_ENTRIES=10 -DROUTE_CONF_DEFAULT_LIFETIME=240 
CFLAGS += -DUART1_CONF_TX_WITH_INTERRUPT=1 

# Nullrdc is less efficiant, but gives better results
CFLAGS += -DNETSTACK_CONF_RDC=nullrdc_driver 
CFLAGS += -DCCA_THRESH="-46"
CFLAGS += -gstabs+
CFLAGS += -DENERGEST_CONF_ON=0


all: sink_mesh.sky statprinter.sky statprinter.ihex burn-nodeid.sky burn-nodeid.ihex base_rssi.sky base_rssi.ihex cca_check.ihex rssi_stat.ihex sink_mesh.ihex

sink_mesh.sky: statpacker.o generator.o

statprinter.sky: generator.o

 


include $(CONTIKI)/Makefile.include

#CFLAGS += -O3
