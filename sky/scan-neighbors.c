#include "contiki.h"
#include "lib/list.h"
#include "lib/memb.h"
#include "lib/random.h"
#include "net/rime.h"
#include "sys/clock.h"
#include <stdio.h>
#include "dev/leds.h"
#include <stddef.h>

#define MAX_NEIGHBORS 16
#define MAX_NODES 16
#define MAX_PACKETS 10
#define TTL 90
#define MAX_PACKSIZE 60

static const rimeaddr_t sink_addr = {
		.u8 = {01, 00}
} ;




/* Struct for neighbor info */
struct neighbor {
	struct neighbor *next;
	unsigned long last_seen;
	unsigned long last_sent;
	rimeaddr_t addr;
	uint16_t last_seqno;
	uint8_t recv_count;
	uint8_t loose_count;
	uint8_t dup_count;
	uint8_t rssi;
	uint8_t lqi;
};


/* Struct for neighbor info */
struct neighbor_sink {
	rimeaddr_t addr;
	uint8_t recv_count;
	uint8_t loose_count;
	uint8_t dup_count;
	uint8_t rssi;
	uint8_t lqi;
};

__attribute__ ((packed)) struct sink_msg{
	struct sinkmsg * next;
	unsigned long stime;
	rimeaddr_t own_addr;
	uint16_t seqno;
	unsigned long time;
	uint8_t ele;
	struct neighbor_sink n[0];
} ;



/* Struct for broadcast messages*/
struct broadcast_message {
	uint16_t seqno;
	rimeaddr_t own_addr;
} ;




/* Struct for node adresses */
struct node_address {
	struct node_address *next;
	rimeaddr_t node_addr;
	uint16_t ttl;
};

/* Structs for connections */
static struct broadcast_conn broadcast;
static struct mesh_conn mesh;

/* Memory send-buffers */
MEMB(sendbuf_memb, struct{uint16_t bla[MAX_PACKSIZE/2];} , MAX_PACKETS);

/* Sendbuf list */
LIST(sndbuf_list);
LIST(rcvbuf_list);



/* Memory pool for list entries */
MEMB(neighbors_memb, struct neighbor, MAX_NEIGHBORS);

/* Neighbors_list */
LIST(neighbors_list);


/*---------------------------------------------------------------------------*/
PROCESS(beacon_process, "Beacon process");
PROCESS(send_process, "Send Process");
PROCESS(agg_process, "Aggregation Process");
PROCESS(serial_process, "Serial output Process");
AUTOSTART_PROCESSES(&beacon_process, &send_process, &agg_process, &serial_process);
//
/*---------------------------------------------------------------------------*/


/*----------------------- BEACONING -----------------------------------------*/

static void broadcast_recv(struct broadcast_conn *c, const rimeaddr_t *from);

static const struct broadcast_callbacks broadcast_call = {broadcast_recv};


PROCESS_THREAD(beacon_process, ev, data){
	static struct etimer et;
	static uint16_t seqno = 1;
	struct broadcast_message msg;

	PROCESS_EXITHANDLER(broadcast_close(&broadcast);)

	PROCESS_BEGIN();
	/* Init */
		etimer_set(&et, CLOCK_SECOND * 2);
		PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));

		broadcast_open(&broadcast, 222, &broadcast_call);
		while(1) {
			/* Send broadcast message every 5 - 10 */
			etimer_set(&et, CLOCK_SECOND * 5 + random_rand() % (CLOCK_SECOND * 5));
			PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));

			/* Prepare message and send broadcast */
			memcpy(&(msg.seqno), &(seqno), sizeof(seqno));
			packetbuf_copyfrom(&msg, sizeof(struct broadcast_message));
			broadcast_send(&broadcast);
			seqno++;
		}
	PROCESS_END();
}


static void
broadcast_recv(struct broadcast_conn *c, const rimeaddr_t *from)
{
	struct neighbor *n;
	struct broadcast_message *bm;
	struct broadcast_message m;
	
	leds_on(LEDS_BLUE);

	bm = packetbuf_dataptr();
	memcpy(&m, bm, sizeof(m));

	/* check if we already know this neighbor. */
	for(n = list_head(neighbors_list); n != NULL; n = list_item_next(n)) {
		if(rimeaddr_cmp(&n->addr, from)) {
			break;
		}
	}

	/* If n is NULL, this neighbor was not found in our list */
	if(n == NULL) {
		n = memb_alloc(&neighbors_memb);
		if(n == NULL) {
			return;
		}
		
		/* Initialize the fields. */
		rimeaddr_copy(&n->addr, from);
		n->recv_count = 0;
		n->dup_count = 0;
		n->loose_count = 0;
		n->last_seqno = -1;
		n->rssi = (packetbuf_attr(PACKETBUF_ATTR_RSSI) + 55);
		n->lqi = (packetbuf_attr(PACKETBUF_ATTR_LINK_QUALITY));

		/* Place the neighbor on the neighbor list. */
		list_add(neighbors_list, n);
	}
	
	/* Set new values - Lowpass*/
	n->rssi = (packetbuf_attr(PACKETBUF_ATTR_RSSI) + 55 + n->rssi)/2;
	n->lqi = (packetbuf_attr(PACKETBUF_ATTR_LINK_QUALITY) + n->lqi)/2;
	n->last_seen = clock_seconds();

	/* Logic */
	if(n->last_seqno == -1){
		// Do nothing....
	} else if(n->last_seqno == m.seqno){
		n->dup_count++;
	} else if(n->last_seqno + 1 == m.seqno){
		// Do nothing
	} else if(n->last_seqno > m.seqno){
		//Reset? -> Ignore
	} else {
		n->loose_count +=  m.seqno - n->last_seqno;
	}

	n->last_seqno = m.seqno;
	n->recv_count++;
	
	leds_off(LEDS_BLUE);
}




/*-------------- DATA TO SINK -------------------------------------------------------*/
static void mesh_sent(struct mesh_conn *c);
static void mesh_timedout(struct mesh_conn *c);
static void mesh_recv(struct mesh_conn *c, const rimeaddr_t *from, uint8_t hops) ;

static const struct mesh_callbacks callbacks = {mesh_recv, mesh_sent, mesh_timedout};

PROCESS_THREAD(agg_process, ev, data)
{
	static struct etimer et;
	static uint16_t seqno;
	#define CPY(dst, src) {memcpy(&dst, &src, sizeof(src));}



	PROCESS_BEGIN();
		short w =  random_rand() % (CLOCK_SECOND * 60) + 2;
		short w2 = w  / CLOCK_SECOND;
		printf("Startupdelay : %hi\n",w2);
		etimer_set(&et, w);
		PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));
		/* Init */
		while(1) {
			/* Send data to sink every 60 + 10% */
			etimer_set(&et, CLOCK_SECOND * 60);
			PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));


			static struct neighbor *n;
			struct sink_msg * sm = NULL;
			uint_fast8_t idx = 0;

			printf("Prep Sinkmsg\n");
			/* Send each neighbordata to sink*/
			for(n = list_head(neighbors_list); n != NULL; n = list_item_next(n)) {
				if(sm == NULL){
					sm = memb_alloc(&sendbuf_memb);
					if(sm == NULL) {
						printf("Something went wrong\n");
						break;
					}
					sm->seqno = seqno++;
					sm->time = clock_seconds();
					rimeaddr_copy(&(sm->own_addr), &rimeaddr_node_addr);
					idx=0;
				}

				struct neighbor_sink *ns;
				ns=&(sm->n[idx]);
				CPY(ns->addr, n->addr);
				CPY(ns->dup_count, n->dup_count);
				CPY(ns->loose_count, n->loose_count);
				CPY(ns->recv_count, n->recv_count);
				CPY(ns->lqi, n->lqi);
				CPY(ns->rssi, n->rssi);
				n->dup_count = 0;
				n->loose_count = 0;
				n->recv_count = 0;
				// TODO REmove
				idx++;
				//Test whether the next packet will fit.
				if((void *)&(sm->n[idx+1]) - (void *)&(sm->own_addr) > MAX_PACKSIZE){
					sm->ele = idx;
					list_add(sndbuf_list, sm);
					sm = NULL;
				}
			}
			if(sm != NULL){
				sm->ele = idx;
				list_add(sndbuf_list, sm);
				sm = NULL;
			}


		}
	PROCESS_END();
}

PROCESS_THREAD(send_process, ev, data)
{
	static struct etimer et;

	PROCESS_EXITHANDLER(mesh_close(&mesh);)


	PROCESS_BEGIN();
		etimer_set(&et, random_rand() % (CLOCK_SECOND * 3) + 2);
		PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));
		mesh_open(&mesh, 120, &callbacks);

		etimer_set(&et, random_rand() % (CLOCK_SECOND * 3) + 2);
		PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));

		while(1){
			if(!rimeaddr_cmp(&rimeaddr_node_addr, &sink_addr )){
				etimer_set(&et, random_rand() % (CLOCK_SECOND * 5) + 10);
			} else {
				etimer_set(&et, random_rand() % (CLOCK_SECOND * 1) + 1);
			}
			PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));
			struct sink_msg * sm;

			if(!mesh_ready(&mesh)){
				printf("Mesh not ready\n");
				continue;
			}

			if((sm = list_pop(sndbuf_list))) {
				if(!rimeaddr_cmp(&rimeaddr_node_addr, &sink_addr )){ //Only send if not sink
					//Queue to the end.
					list_add(sndbuf_list, sm);
					if(clock_seconds() - sm->stime < 15){
						printf("Not ready to resend: %i\n", (int) (clock_seconds() - sm->stime));
					} else {
						printf("sending to sink %i\n", sm->seqno);
						sm->stime = clock_seconds();
						packetbuf_copyfrom(&(sm->own_addr), (char*)&(sm->n[sm->ele+1]) - (char*)&(sm->own_addr));
						mesh_send(&mesh, &sink_addr);
					}
				} else {
					printf("sending to source: %i\n",  sm->seqno);
					packetbuf_copyfrom(&(sm->own_addr), (char*)&(sm->n[0]) - (char*)&(sm->own_addr));
					rimeaddr_t src;
					rimeaddr_copy(&src, &(sm->own_addr));
					mesh_send(&mesh,&src);
					memb_free(&sendbuf_memb, sm);
				}
			}

		}

	PROCESS_END();
}

static void
mesh_sent(struct mesh_conn *c)
{
	//printf("packet sent %d.%d \n", c->queued_data_dest.u8[0],c->queued_data_dest.u8[1]);
	printf("packet sent\n");
	leds_off(LEDS_RED);
}
static void
mesh_timedout(struct mesh_conn *c)
{
	printf("packet timed out\n");
	leds_off(LEDS_RED);
}
static void mesh_recv(struct mesh_conn *c, const rimeaddr_t *from, uint8_t hops) {

	printf("Got mesh\n");
	if(!rimeaddr_cmp(&rimeaddr_node_addr, &sink_addr )){ //I'm not sink
		struct sink_msg * sm = packetbuf_dataptr() - offsetof(struct sink_msg, own_addr	);
		struct sink_msg * smb;
		uint16_t seqno;
		CPY(seqno, sm->seqno);
		printf("SEQ: %i\n", sm->seqno);
		for(smb = list_head(sndbuf_list); smb != NULL; smb = list_item_next(smb)) {
			if(smb->seqno == seqno){
				list_remove(sndbuf_list, smb);
				memb_free(&sendbuf_memb, smb);
				printf("BUF: Freed %i\n",smb->seqno);
				break;
			} else {
				printf("BUF: Got %i\n", smb->seqno);
			}
		}

	} else { //Sink
		struct sink_msg * smb;
		smb = memb_alloc(&sendbuf_memb);
		if(smb == NULL){
			printf("A horrible problem!");
			return;
		}
		packetbuf_copyto(&(smb->own_addr));
		list_add(rcvbuf_list, smb);
	}

}

/*---------------------------------------------------------------------------*/


/*---------------------------------------------------------------------------*/






/*--------------------------------- SERIAL OUTPUT ----------------------------------*/


PROCESS_THREAD(serial_process, ev, data)
{
	static struct etimer et;


	PROCESS_BEGIN();
		//while(1){
		//PROCESS_WAIT_EVENT_UNTIL(ev == serial_line_event_message && data != NULL);
		if(rimeaddr_cmp(&rimeaddr_node_addr, &sink_addr )){ //Only run as sink!
			printf("I am Sink!!!\n");
			while(1){
				etimer_set(&et, CLOCK_SECOND/4);
				PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));
				struct sink_msg * sm;
				while((sm = list_pop(rcvbuf_list))) {
					printf("DAT: %x %x %lx\n", *(uint16_t*)&(sm->own_addr), sm->seqno, sm->time);
					uint_fast8_t i;
					for(i = 0; i < sm->ele; i++){
						struct neighbor_sink * ns =  &(sm->n[i]);
						printf("%x %x %x %x %x %x\n", *(uint16_t*)&(ns->addr), ns->rssi, ns->lqi, ns->recv_count, ns->loose_count, ns->dup_count);
					}
					printf("---\n");
					list_add(sndbuf_list, sm);
				}

			}
		} else {
			printf("I am not Sink: %i.%i!!!\n", rimeaddr_node_addr.u8[0], rimeaddr_node_addr.u8[1]);
		}

	PROCESS_END();
}


/*--------------------------------------- Cleanup ----------------------------------- */

#if 0

PROCESS_THREAD(poll_process, ev, data) {
	static struct etimer et;
	static uint16_t isSink = 0;
	static rimeaddr_t sink_addr;
	struct broadcast_message hello;
	struct node_address *node;

	PROCESS_EXITHANDLER(mesh_close(&mesh);)
	PROCESS_BEGIN();

		/* Init */
		etimer_set(&et, CLOCK_SECOND * 2);
		PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));

		/* If Sink: Add addr to Nodes */
		if( rimeaddr_cmp(rimeaddr_node_addr, sink_addr)) {
			struct node_address *sink;
			sink = memb_alloc(&address_memb);
			rimeaddr_copy(&sink->node_addr,&rimeaddr_node_addr);
			sink->ttl = -1;
			list_add(node_addresses, sink);
			isSink = 1;
			printf("I am sink\n");
			clock_init();
		}

		etimer_set(&et, CLOCK_SECOND * 2);
		PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));

		mesh_open(&mesh, 120, &callbacks);


		while(1) {

			/* Send hello message every 4 - 8 */
			etimer_set(&et, CLOCK_SECOND * 4 + random_rand() % (CLOCK_SECOND * 4));
			PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));

			if(!isSink) {
				uint16_t dummy = 1;
				memcpy(&(hello.seqno), &(dummy), sizeof(dummy));
				rimeaddr_copy(&hello.own_addr, &rimeaddr_node_addr);
				packetbuf_copyfrom(&hello, sizeof(hello));
				mesh_send(&mesh, &sink_addr);
				leds_on(LEDS_RED);
				if(route_lookup(&sink_addr)) {
					leds_on(LEDS_GREEN);
				}
				else {
					leds_off(LEDS_GREEN);
				}
			}
			/* Remove node or decrease TTL */
			for(node = list_head(node_addresses); node != NULL; node = list_item_next(node)) {
				if(node->ttl == 0) {
					list_remove(node_addresses, node);
				} else {
					node->ttl--;
				}
			}
		}
	PROCESS_END();
}
#endif
