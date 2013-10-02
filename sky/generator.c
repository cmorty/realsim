#include "contiki.h"
#include "net/rime.h"
#include "lib/memb.h"
#include "sys/clock.h"
#include "lib/random.h"
#include "dev/leds.h"
#include "lib/random.h"
#include "stdio.h"
#include "ringbuf.h"
#include "string.h"

#include "generator.h"
#include "handlestats.h"

#include "dev/ds2411.h"

#include "dev/cc2420.h"

/**
 * See Datasheet or CC2420.java
 * Might be 55 or 53 or whatever
 */
#define RSSI_OFFSET (-45)



#if BEACON_PAUSE_MIN * 2 * BEACONS_PER_PERIODE > BEACONS_PERIODE
#error "Can not guarantee number of beacons";
#endif


static uint16_t beacontimer[BEACONS_PER_PERIODE];
static uint8_t beacontimerpos = BEACONS_PER_PERIODE;


#define MAX_NEIGHBORS 50

//Ringbuf
struct idata{
	rimeaddr_t src;
	int8_t lqi;
	int8_t rssi;
	uint16_t seqno;
};

#define BUFS 32

static struct idata idatabuf[BUFS];
static volatile uint8_t bufuse[BUFS];
static uint8_t inbufdata[BUFS];
static struct ringbuf inbuf;

static struct broadcast_conn broadcast;

/* Struct for broadcast messages*/
struct broadcast_message {
	uint16_t seqno;
	rimeaddr_t own_addr;
} ;


/* Memory pool for list entries */
MEMB(neighbors_memb, struct neighbor, MAX_NEIGHBORS);

/* Neighbors_list */
LIST(neighbors_list);




PROCESS(beacon_process, "Beacon process");

#define SUNIT 64

#define BEACON_PAUSE_MIN_U (BEACON_PAUSE_MIN * SUNIT)
#define BEACONS_PERIODE_U (BEACONS_PERIODE * SUNIT)

/*----------------------- BEACONING -----------------------------------------*/

static void setbeacontimer(){
	static uint16_t last = BEACON_PAUSE_MIN_U;
	uint8_t i;
	for(i = 0; i < BEACONS_PER_PERIODE; i++){
		while(1){
			uint16_t b = random_rand() % BEACONS_PERIODE_U;
			//Check for the beginning
			if(b + last < BEACON_PAUSE_MIN_U) continue;
			uint8_t s;
			//Check distance to others
			for(s = 0; s < i; s++){
				if(b + BEACON_PAUSE_MIN_U < beacontimer[s]) break;
			}
			if(s > 0 &&  b < beacontimer[s-1] + BEACON_PAUSE_MIN_U){
				continue;
			}
			uint16_t l = b;
			for(;s < i + 1; s++){
				uint16_t t = beacontimer[s];
				beacontimer[s] = l;
				l = t;
			}
			break;
		}
	}
	uint16_t tlast = BEACONS_PERIODE_U - beacontimer[BEACONS_PER_PERIODE - 1];
	//calculate offsets

	for(i = BEACONS_PER_PERIODE - 1; i > 0 ; i--){
		beacontimer[i] -= beacontimer[i-1];
	}
	beacontimer[0] += last;

	printf("BC: ");
	for(i = 0; i < BEACONS_PER_PERIODE ; i++){
		printf("%i ",  beacontimer[i]);
	}
	puts("");
	last = tlast;
}

static void push_stats(struct neighbor *n){
	handlestats(n);
	memset(&(n->recv_count), 0 , sizeof(struct neighbor) - offsetof(struct neighbor, recv_count) );

}




/**
 * Handles received packets in a protothread to avoid concurrency
 */
static int handlepackets(void){
	struct neighbor *n;
	int id = ringbuf_get(&inbuf);
	if(id != -1){



		/* check if we already know this neighbor. */
		for(n = list_head(neighbors_list); n != NULL; n = list_item_next(n)) {
			if(rimeaddr_cmp(&n->addr, &idatabuf[id].src)) {
				break;
			}
		}


		/* If n is NULL, this neighbor was not found in our list
		 * Create new entry
		 */
		if(n == NULL) {
			n = memb_alloc(&neighbors_memb);
			if(n == NULL) {
				//Continue to next;
				bufuse[id] = 0;
				puts("W: Too many neighbours");
				return 1;
			}
			memset(n, 0, sizeof(*n));

			/* Initialize the fields. */
			rimeaddr_copy(&n->addr, &idatabuf[id].src);
			n->last_seqno = -1;

			/* Place the neighbor on the neighbor list. */
			list_add(neighbors_list, n);
		}

		// Test
		if(n->last_seqno != -1 && n->last_seqno / BEACONS_PER_PERIODE != idatabuf[id].seqno/ BEACONS_PER_PERIODE){
			push_stats(n);
		}

		/* Set new values - Lowpass*/
		n->rssi += idatabuf[id].rssi;
		n->lqi += idatabuf[id].lqi;
		n->last_seen = clock_seconds();

		/* Logic */
		if(n->last_seqno == idatabuf[id].seqno){
			n->dup_count++;
		}

		n->last_seqno = idatabuf[id].seqno;
		n->recv_count++;
		bufuse[id] = 0;
		return 1;
	}

	//Search for neighbors, that will not receive any data for that period any more

	for(n = list_head(neighbors_list); n != NULL; n = list_item_next(n)) {
		if(clock_seconds() - n->last_seen > BEACONS_PERIODE * 2 ) {
			n->remove = 1;
			push_stats(n);
			list_remove(neighbors_list, n);
			memb_free(&neighbors_memb, n);
			//As we don't know the previous sibling we just stop here - others will be removed the next time
			break;
		}
	}
	return 0;
}



static void broadcast_recv(struct broadcast_conn *c, const rimeaddr_t *from);

static const struct broadcast_callbacks broadcast_call = {broadcast_recv};
process_event_t rcv_event;


static volatile uint8_t handling_packets;
PROCESS_THREAD(beacon_process, ev, data){
	static struct etimer et;
	static uint16_t seqno = 1;
	struct broadcast_message msg;


	PROCESS_EXITHANDLER(broadcast_close(&broadcast);)

	PROCESS_BEGIN();
	/* Init */
		#ifdef CCA_THRESH
		  printf("Setting CCA to: %d\n", CCA_THRESH);
		  cc2420_set_cca_threshold(CCA_THRESH);
		#endif //CCA_THRESH
		random_init(*(long *)ds2411_id + *(long * )&rimeaddr_node_addr);
		ringbuf_init(&inbuf, inbufdata, sizeof(inbufdata));
		if(!rcv_event) rcv_event = process_alloc_event();
		etimer_set(&et, (random_rand() % BEACONS_PERIODE_U) * CLOCK_SECOND / SUNIT);
		PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));
		broadcast_open(&broadcast, 222, &broadcast_call);
		etimer_set(&et, CLOCK_SECOND * 2);
		static int handll;
		while(1) {
			//Set timer
			if(beacontimerpos == BEACONS_PER_PERIODE){
				setbeacontimer();
				beacontimerpos = 0;
			}
			//Wait for timer of data
			if(!handlepackets() || handll > 10){
				static int mon;
				handling_packets = 0;
				if(handll > 10){
					if(1 || mon++ > 10){
						int full = 0;
						int i;
						for(i = 0; i < BUFS; i++) if(bufuse[i] != 0) full++ ;
						printf("J: %x\n", full);
						mon = 0;
					}
					process_post(&beacon_process, rcv_event ,NULL);
				} else {
					mon = 0;
				}
				PROCESS_YIELD();
				handll = 0;
				handling_packets = 1;
			} else {
				handll ++;

			}
			//Beaconning!
			if(etimer_expired(&et)){
				etimer_reset_set(&et, beacontimer[beacontimerpos] * CLOCK_SECOND / SUNIT);

				beacontimerpos++;

				leds_on(LEDS_GREEN);

				/* Prepare message and send broadcast */
				memcpy(&(msg.seqno), &(seqno), sizeof(seqno));
				packetbuf_copyfrom(&msg, sizeof(struct broadcast_message));
				broadcast_send(&broadcast);


				seqno++;
				leds_off(LEDS_GREEN);
			}

			//Handle Received data


		}
	PROCESS_END();
}


static void
broadcast_recv(struct broadcast_conn *c, const rimeaddr_t *from)
{

	struct broadcast_message *bm;
	struct broadcast_message m;

	leds_on(LEDS_BLUE);


	//Put into buffer
	uint_fast8_t i;


	for(i = 0; i < BUFS; i++){

		if(bufuse[i] != 0) continue; //Search next;
		bufuse[i] = 1;

		bm = packetbuf_dataptr();
		memcpy(&m, bm, sizeof(m));
		rimeaddr_copy(&(idatabuf[i].src), from);
		idatabuf[i].seqno = m.seqno;
		idatabuf[i].lqi = (packetbuf_attr(PACKETBUF_ATTR_LINK_QUALITY));
		idatabuf[i].rssi = (int8_t)(packetbuf_attr(PACKETBUF_ATTR_RSSI));
		ringbuf_put(&inbuf, i);

		if(!handling_packets) process_post(&beacon_process, rcv_event ,NULL);
		break;
	}
	if( i == BUFS){
		printf("Temp-buffer full");
	}

	leds_off(LEDS_BLUE);
}


