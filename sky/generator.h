
#ifndef GENERATOR_H
#define GENERATOR_H

#include <stdint.h>
#include "net/rime/rime.h"
#define BEACON_PAUSE_MIN 2
#define BEACONS_PER_PERIODE 8
#define BEACONS_PERIODE 80
#define BRSSI_TIME_OFFSET 250	// offset in ms
#define BRSSI_TIME_RANDOM 100	// offset + random ms

/* Struct for neighbor info */
struct neighbor {
	struct neighbor *next;
	unsigned long last_seen;
	unsigned long last_sent;
	linkaddr_t addr;
	uint16_t last_seqno;
	uint8_t recv_count;
	uint8_t dup_count;
	int16_t rssi;
	uint16_t lqi;
	struct {
		uint8_t remove:1;
	};
};

struct brssi {
	int8_t brssi_min;
	int8_t brssi_max;
	int8_t brssi_avg;
#if(BEACONS_PERIODE * 1000 / BRSSI_TIME_OFFSET) > 65535
	#error "datatype of counter for bRSSI too small with this timings."
#endif
	int32_t brssi_sum;
	uint16_t counter;
};

PROCESS_NAME(beacon_process);
PROCESS_NAME(beacon_rssi_process);


#endif
