
#include "contiki.h"
#include "generator.h"
#include "handlestats.h"
#include "stdio.h"

AUTOSTART_PROCESSES(&beacon_process);

void handlestats(struct neighbor *n)
{
	printf("S: %x %x %x %x %x %x\n",
			*(uint16_t*)&(n->addr),
			n->rssi /  n->recv_count,
			n->lqi /  n->recv_count,
			n->recv_count,
			BEACONS_PER_PERIODE  -  n->recv_count,
			n->dup_count);



}
