#include "lib/memb.h"
#include "stddef.h"
#include <stdio.h>
#include "net/rime.h"
#include "lib/list.h"
#include "sys/energest.h"
#include <string.h>
#include "statpacker.h"

#define CPY(dst, src) {memcpy(&(dst), &(src), sizeof(dst));}
#define CPYV(dst, src) {typeof(dst) t ; memcpy(&(dst), (t = src, &t), sizeof(dst));}


MEMB(sendbuf_memb, stat_datapack, MAX_PACKETS);
LIST(sndbuf_list);

static uint16_t free_arr[10];

struct scan_stats scan_stats = {0,0};

static uint16_t seqno;

void stat_print(struct sink_msg * sm){
	//MSP430 Make data is aligned
	char * pos;
	char * end;
	rimeaddr_t * rad;
	struct sink_msg lsm;
	if((uintptr_t)sm % 2){
		CPY(lsm, *sm);
		sm = &lsm;
	}
	pos = (void *)sm;
	end = pos + sm->size;

	printf("DAT: %lx %x %x %lx %x\n",clock_seconds(),  *(uint16_t*)&(sm->own_addr), sm->seqno, sm->time, sm->interval);
	pos = sm->d;

	while(1){
		rad = (void * )pos;
		if(pos > end) break;
		if(rimeaddr_cmp(&(sm->own_addr), rad)){
			//Energy
			struct energystats * es = (void *) pos;
			printf("E: %x %lx %lx %lx %lx %lx\n", *(uint16_t*)&(sm->own_addr),  es->time, es->cpu, es->lpm, es->listen, es->transmit);
			pos = (void *)(es + 1);

		} else {
			struct neighbor_sink * ns = (void * )pos;
			pos = (void *)(ns + 1);
			printf("R: %x %x %x %x %x %x\n", *(uint16_t*)&(ns->addr), ns->rssi, ns->lqi, ns->recv_count, ns->loose_count, ns->dup_count);
		}
	}
}

struct sink_msg * stat_getnext(void){
	//Make sure everything is removed before something is poped
	stat_flush();
	struct sink_msg * rv = list_pop(sndbuf_list);
	if(rv != NULL){
		list_add(sndbuf_list, rv);
	}
	return rv;
}


void stat_free(uint16_t id){
	uint8_t i;
	for(i = 0; i < (sizeof(free_arr)/sizeof(free_arr[0])); i++){
		if(!free_arr[i]){
			free_arr[i] = id + 1 ;
			return;
		}
	}

}

void stat_flush(void){
	static struct sink_msg * sm = NULL;


	//Flush packets
	uint8_t i;
	for(i = 0; i < (sizeof(free_arr)/sizeof(free_arr[0])); i++){
		if(free_arr[i]){
			for(sm = list_head(sndbuf_list); sm != NULL; sm = list_item_next(sm)) {
				if(sm->seqno + 1 == free_arr[i]){
					list_remove(sndbuf_list, sm);
					memb_free(&sendbuf_memb, sm);
					printf("BUF: Freed %i\n",sm->seqno);
					break;
				}
			}
			free_arr[i] = 0;
		}
	}

}



static void * reserve(uint16_t size){
	static struct sink_msg * sm = NULL;
	const uint8_t hdrsize = sizeof(struct sink_msg) - offsetof(struct sink_msg, pstart);
	stat_flush();

	// Check where it fits into the current packet
	if(sm != NULL && sizeof(struct sink_msg) + sm->size + size > MAX_PACKSIZE){
		list_add(sndbuf_list, sm);
		sm = NULL;
	}
	if(sm == NULL){
		sm = memb_alloc(&sendbuf_memb);
		if(sm == NULL) {
			scan_stats.loss++;
			printf("Out of buffer memory\n");
			return NULL;
		}
		sm->interval = BEACONS_PERIODE;
		sm->seqno = seqno++;
		sm->time = clock_seconds();
		sm->stime = 0;
		rimeaddr_copy(&(sm->own_addr), &rimeaddr_node_addr);
		sm->size = hdrsize;
	}

	sm->size += size;
	return &(sm->d[sm->size - hdrsize - size] );
}


void pack_energystats(void){
	struct energystats * es = reserve(sizeof(struct energystats));
	if(es == NULL) return;



	rimeaddr_copy(&(es->addr), &rimeaddr_node_addr);
	CPYV(es->cpu, energest_type_time(ENERGEST_TYPE_CPU));
	CPYV(es->lpm, energest_type_time(ENERGEST_TYPE_LPM));
	CPYV(es->transmit, energest_type_time(ENERGEST_TYPE_TRANSMIT));
	CPYV(es->listen, energest_type_time(ENERGEST_TYPE_LISTEN));
	CPYV(es->time , clock_seconds());
	printf("Pushing energy\n");
	printf("EDE: %x %lx %lx %lx %lx %lx\n", *(uint16_t*)&(rimeaddr_node_addr),  es->time, es->cpu, es->lpm, es->listen, es->transmit);
}


void handlestats(struct neighbor *n)
{
	struct neighbor_sink * ns = reserve(sizeof(struct neighbor_sink));
	if(ns == NULL) return;

	CPY(ns->addr, n->addr);
	CPY(ns->dup_count, n->dup_count);
	n->dup_count = 0;

	CPY(ns->recv_count, n->recv_count);
	n->recv_count = 0;

	CPYV(ns->loose_count,  BEACONS_PER_PERIODE  -  n->recv_count);

	CPYV(ns->lqi, n->lqi /  n->recv_count);
	n->lqi = 0;

	CPYV(ns->rssi,  n->rssi /  n->recv_count);
	n->rssi = 0;
	printf("Pushing stats for %d.%d\n",n->addr.u8[0],n->addr.u8[1]);
	printf("RDE: %x %x %x %x %x %x\n", *(uint16_t*)&(ns->addr), ns->rssi, ns->lqi, ns->recv_count, ns->loose_count, ns->dup_count);

}


