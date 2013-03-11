#include "contiki.h"
#include "lib/list.h"
#include "lib/memb.h"
#include "lib/random.h"
#include "net/rime.h"
#include "sys/clock.h"
#include <stdio.h>
#include "dev/leds.h"
#include <stddef.h>
#include "serial-line.h"
#include <lib/print-stats.h>
#include <stdint.h>
#include "statpacker.h"
#include <string.h>




/*-------------- DATA TO SINK -------------------------------------------------------*/
static void mesh_sent(struct mesh_conn *c);
static void mesh_timedout(struct mesh_conn *c);
static void mesh_recv(struct mesh_conn *c, const rimeaddr_t *from, uint8_t hops) ;
static struct mesh_conn mesh;



#ifndef SINKADDR
#define SINKADDR 0x0001
#endif

const rimeaddr_t sink_addr = {
		.u8 = {(SINKADDR) & 0xFF, ((SINKADDR) >> 8) & 0xFF}
};


static uint8_t issink(void){
	return rimeaddr_cmp(&sink_addr,&rimeaddr_node_addr );
}



#define CPY(dst, src) {memcpy(&(dst), &(src), sizeof(src));}
PROCESS_NAME(sink_process);
PROCESS(send_process, "Send Process");
PROCESS(energy_process, "Energy Process");
AUTOSTART_PROCESSES( &send_process, &sink_process, &beacon_process, &energy_process);


// ========================= Nodes ============================



PROCESS_THREAD(energy_process, ev, data)
{
	static struct etimer et;

	PROCESS_BEGIN();
		etimer_set(&et, 60 * CLOCK_SECOND);
		while(1){
			PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));
			pack_energystats();
			etimer_reset(&et);
		}
	PROCESS_END();
}

PROCESS_THREAD(send_process, ev, data)
{
	static struct etimer et;
	static const struct mesh_callbacks callbacks = {mesh_recv, mesh_sent, mesh_timedout};

	PROCESS_EXITHANDLER(mesh_close(&mesh);)


	PROCESS_BEGIN();

		if(issink()){
			PROCESS_EXIT();
		}
		printf("I am not Sink: %x.%x. Sink is: %x.%x\n", rimeaddr_node_addr.u8[0], rimeaddr_node_addr.u8[1],sink_addr.u8[0], sink_addr.u8[1]);
		//Wait some time
		etimer_set(&et, random_rand() % (3 * CLOCK_SECOND) + 2 * CLOCK_SECOND);
		PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));

		//Open Network
		mesh_open(&mesh, 120, &callbacks);

		etimer_set(&et, random_rand() % (60 * CLOCK_SECOND) + 2 * CLOCK_SECOND);
		PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));

		while(1){
			struct sink_msg * sm;
			// Wait 5 to 15 sec
			etimer_set(&et, random_rand() % (10 * CLOCK_SECOND) + 5);
			PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));

			// Make sure, there is nothing in the queue
			if(!mesh_ready(&mesh)){
				printf("Mesh not ready\n");
				continue;
			}

			if((sm = stat_getnext())) {

				//Make sure there are at least 15s between sending data to the sink
				if(clock_seconds() - sm->stime < 15){
					printf("Not ready to resend: %i\n", (int) (clock_seconds() - sm->stime));
				} else {
					leds_on(LEDS_RED);
					printf("sending to sink %i\n", sm->seqno);
					if(sm->stime != 0) scan_stats.resend++;
					sm->stime = clock_seconds();
					printf("size: %d\n", sm->size);
					uint16_t cp = packetbuf_copyfrom(&(sm->pstart), sm->size);
					if(cp != sm->size){
						printf("ERROR: copyfrom %d / %d\n", cp , sm->size);
					}
					mesh_send(&mesh, &sink_addr);
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

	struct sink_msg * sm = packetbuf_dataptr() - offsetof(struct sink_msg, own_addr	);
	uint16_t seqno;
	CPY(seqno, sm->seqno);
	stat_free(seqno);
}

//======================== Sink ================================

uint8_t uart1_active(void);

PROCESS(sink_process, "Send Process");


static void mesh_sent_sink(struct mesh_conn *c);
static void mesh_timedout_sink(struct mesh_conn *c);
static void mesh_recv_sink(struct mesh_conn *c, const rimeaddr_t *from, uint8_t hops) ;

#define RCVBUFSIZE 5
static  stat_datapack rcvbuf_memb[RCVBUFSIZE];

#define SNDBUFSIZE 10
static struct sink_msg sndbuf_memb[SNDBUFSIZE];



PROCESS_THREAD(sink_process, ev, data)
{
	static struct etimer et;
	static const struct mesh_callbacks callbacks = {mesh_recv_sink, mesh_sent_sink, mesh_timedout_sink};

	PROCESS_EXITHANDLER(mesh_close(&mesh);)


	PROCESS_BEGIN();
		static uint8_t sndpos = 0;
		if(!issink()){
			PROCESS_EXIT();
		}
		//Wait some time
		etimer_set(&et, random_rand() % (3 * CLOCK_SECOND) + 2 * CLOCK_SECOND);
		PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));

		//Open Network
		mesh_open(&mesh, 120, &callbacks);
		printf("I am not Sink: %x.%x. Sink is: %x.%x\n", rimeaddr_node_addr.u8[0], rimeaddr_node_addr.u8[1],sink_addr.u8[0], sink_addr.u8[1]);
		printf("I am Sink!!!\n");
		while(1){

			etimer_set(&et, CLOCK_SECOND/8);

			PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));



			//Print stats

			if(! uart1_active()){
				uint8_t i;
				for(i = 0; i < RCVBUFSIZE; i++ ){
					struct sink_msg * sm = (void *)(rcvbuf_memb[i]);
					if(sm->size != 0){
						uint8_t i;
						stat_print(sm);
						for(i = 0; i < SNDBUFSIZE; i++){
							if(sndbuf_memb[i].size == 0){
								sndbuf_memb[i].time = 0;
								CPY(sndbuf_memb[i].seqno,sm->seqno);
								rimeaddr_copy(&(sndbuf_memb[i].own_addr), &(sm->own_addr));
								sndbuf_memb[i].size = sizeof(struct sink_msg) - offsetof(struct sink_msg, pstart);
								break;
							}
						}
						if(i == SNDBUFSIZE){
							printf("Output buffer full.\n");
							scan_stats.loss++;
						}

						sm->size = 0;
						goto sendpack;
					}
				}


				struct sink_msg * sm;

				if((sm = stat_getnext())) {
					stat_print(sm);
					stat_free(sm->seqno);
					goto sendpack;
				}
			}
			sendpack:
			//Send out packts
			if(mesh_ready(&mesh)){
				uint8_t sndposs = sndpos;
				do{
					sndpos = (sndpos+1) % SNDBUFSIZE;
					if(sndbuf_memb[sndpos].size != 0){
						packetbuf_copyfrom(&(sndbuf_memb[sndpos].pstart), sndbuf_memb[sndpos].size);
						static rimeaddr_t dst;
						rimeaddr_copy(&dst, &(sndbuf_memb[sndpos].own_addr));
						printf("sending %i to source: %x.%x\n",  sndbuf_memb[sndpos].seqno, dst.u8[0], dst.u8[1]);
						mesh_send(&mesh,&dst);
						sndbuf_memb[sndpos].size = 0;
						break;
					}
				} while(sndposs!=sndpos);

			}

		}

	PROCESS_END();
}

static void
mesh_sent_sink(struct mesh_conn *c)
{
	//printf("packet sent %d.%d \n", c->queued_data_dest.u8[0],c->queued_data_dest.u8[1]);
	printf("packet sent\n");
	leds_off(LEDS_RED);
}
static void
mesh_timedout_sink(struct mesh_conn *c)
{
	printf("packet timed out\n");
	leds_off(LEDS_RED);
}

static void mesh_recv_sink(struct mesh_conn *c, const rimeaddr_t *from, uint8_t hops) {

	uint8_t i;
	for(i = 0; i < RCVBUFSIZE; i++ ){
		struct sink_msg * sm = (void *)(rcvbuf_memb[i]);
		if(sm->size == 0){
			sm->size=packetbuf_copyto(&(sm->pstart));
			break;
		}
	}

	if(i == RCVBUFSIZE){
		printf("Input buffer full.\n");
		scan_stats.loss++;
		return;
	}



}
