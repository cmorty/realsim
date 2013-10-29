
#include "contiki.h"
#include "generator.h"
#include "handlestats.h"
#include "stdio.h"
#include "net/rime.h"
#include "uart1.h"

AUTOSTART_PROCESSES(&beacon_process);




void handlestats(struct neighbor *n)
{

/*
	printf("R: %x %x %x %x %x %x %x\n",
			*(uint16_t*)&(rimeaddr_node_addr),
			*(uint16_t*)&(n->addr),
			n->rssi /  n->recv_count,
			n->lqi /  n->recv_count,
			n->recv_count,
			BEACONS_PER_PERIODE  -  n->recv_count,
			n->dup_count);*/
	/*
	printf("RE: %x %x %x %x %x %x %x %x\n",
					*(uint16_t*)&(rimeaddr_node_addr),
					*(uint16_t*)&(n->addr),
					n->last_seqno / BEACONS_PER_PERIODE,
					n->rssi /  n->recv_count,
					n->lqi /  n->recv_count,
					n->recv_count,
					BEACONS_PER_PERIODE  -  n->recv_count,
					n->dup_count);
	*/
	printf("RE2: %i %i %i %i %i %i %i %i\n",
				*(uint16_t*)&(rimeaddr_node_addr),
				*(uint16_t*)&(n->addr),
				n->last_seqno / BEACONS_PER_PERIODE,
				n->rssi /  n->recv_count,
				n->lqi /  n->recv_count,
				n->recv_count,
				BEACONS_PER_PERIODE  -  n->recv_count,
				n->dup_count);
	if(n->remove){
		/*printf("DIS: %x %x\n",
							*(uint16_t*)&(rimeaddr_node_addr),
							*(uint16_t*)&(n->addr));*/
		printf("DIS2: %i %i\n",
							*(uint16_t*)&(rimeaddr_node_addr),
							*(uint16_t*)&(n->addr));
	}
}
