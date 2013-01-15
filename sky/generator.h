
#ifndef GENERATOR_H
#define GENERATOR_H

#include <stdint.h>
#include "net/rime.h"
#define BEACON_PAUSE_MIN 2
#define BEACONS_PER_PERIODE 6
#define BEACONS_PERIODE 60

/* Struct for neighbor info */
struct neighbor {
	struct neighbor *next;
	unsigned long last_seen;
	unsigned long last_sent;
	rimeaddr_t addr;
	uint16_t last_seqno;
	uint8_t recv_count;
	uint8_t dup_count;
	uint16_t rssi;
	uint16_t lqi;
};


PROCESS_NAME(beacon_process);



#endif
