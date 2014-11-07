#ifndef STATMAKER_H
#define STATMAKER_H

#include "generator.h"


#define MAX_PACKSIZE 60
#define MAX_PACKETS 10


struct scan_stats{
	uint32_t resend;
	uint32_t loss;
};

extern struct scan_stats scan_stats;
/* Struct for neighbor info */
struct neighbor_sink {
	linkaddr_t addr;
	uint8_t recv_count;
	uint8_t loose_count;
	uint8_t dup_count;
	int8_t rssi;
	uint8_t lqi;
};


__attribute__ ((packed)) struct sink_msg{
	struct sinkmsg * next;
	unsigned long stime;
	uint8_t size;
	// The actual packet starts here.
	union{
		char pstart;
		linkaddr_t own_addr;
	};
	uint16_t interval;
	uint16_t seqno;
	unsigned long time;
	char d[0];
} ;

struct energystats {
	linkaddr_t addr;
	unsigned long cpu;
	unsigned long lpm;
	unsigned long transmit;
	unsigned long listen;
	unsigned long time;
};

typedef char stat_datapack [MAX_PACKSIZE + offsetof(struct sink_msg, pstart)];

void pack_energystats(void);
struct sink_msg * stat_getnext(void);
void stat_flush(void);
void stat_free(uint16_t id);
void stat_print(struct sink_msg * sm);
void pack_brssistats(struct brssi);

#endif
